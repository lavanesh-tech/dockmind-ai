package com.dockmind.recovery.application;

import com.dockmind.common.web.ApiException;
import com.dockmind.common.web.ResourceNotFoundException;
import com.dockmind.recovery.RecoveryProperties;
import com.dockmind.recovery.api.DisruptionResponse;
import com.dockmind.recovery.api.ReportDisruptionRequest;
import com.dockmind.recovery.client.AppliedSchedule;
import com.dockmind.recovery.client.ApplyRequest;
import com.dockmind.recovery.client.DockSchedulingClient;
import com.dockmind.recovery.client.DockSnapshot;
import com.dockmind.recovery.domain.AuditEntry;
import com.dockmind.recovery.domain.AuditEntryRepository;
import com.dockmind.recovery.domain.Disruption;
import com.dockmind.recovery.domain.DisruptionRepository;
import com.dockmind.recovery.domain.DisruptionType;
import com.dockmind.recovery.domain.StoredPlan;
import com.dockmind.recovery.domain.StoredPlanRepository;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.recovery.RecoveryOutcome;
import com.dockmind.scheduling.recovery.RecoveryPlan;
import com.dockmind.scheduling.recovery.RecoveryPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Report → plans → human decision → apply, with every step written to the audit trail.
 *
 * <p>Remote calls happen <b>outside</b> database transactions: holding a transaction (and a
 * connection) open while waiting on another service would let a slow dependency exhaust the pool.
 * Optimistic locking on {@link Disruption} makes concurrent approvals safe: only one wins.
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final DisruptionRepository disruptions;
    private final StoredPlanRepository plans;
    private final AuditEntryRepository audit;
    private final DockSchedulingClient dock;
    private final RecoveryProperties properties;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;
    private final RecoveryPlanner planner = new RecoveryPlanner();

    public RecoveryService(DisruptionRepository disruptions, StoredPlanRepository plans, AuditEntryRepository audit,
                           DockSchedulingClient dock, RecoveryProperties properties, TransactionTemplate tx,
                           JsonMapper json, Clock clock) {
        this.disruptions = disruptions;
        this.plans = plans;
        this.audit = audit;
        this.dock = dock;
        this.properties = properties;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    public DisruptionResponse report(String tenantId, String user, String token, ReportDisruptionRequest request) {
        Instant now = clock.instant();
        Instant decisionTime = request.decisionTime() != null ? request.decisionTime() : now;
        DockSnapshot snapshot = dock.snapshot(request.scheduleDate(), token);

        SchedulingProblem problem = SnapshotMapping.problem(snapshot);
        com.dockmind.scheduling.recovery.Disruption engineDisruption;
        Disruption record;
        List<ApplyRequest.BlockedInput> outages;
        switch (request.type()) {
            case LATE_ARRIVAL -> {
                if (request.shipmentId() == null || request.newArrival() == null) {
                    throw ApiException.badRequest("A late arrival needs shipmentId and newArrival.");
                }
                engineDisruption = new com.dockmind.scheduling.recovery.Disruption.LateArrival(
                        request.shipmentId().toString(), request.newArrival());
                record = Disruption.lateArrival(tenantId, request.scheduleDate(), request.shipmentId(), request.newArrival(),
                        decisionTime, snapshot.baseRunId(), user, now);
                outages = List.of();
            }
            case DOCK_OUTAGE -> {
                if (request.dockCode() == null || request.outageStart() == null || request.outageEnd() == null
                        || !request.outageEnd().isAfter(request.outageStart())) {
                    throw ApiException.badRequest("A dock outage needs dockCode and an outageStart before outageEnd.");
                }
                engineDisruption = new com.dockmind.scheduling.recovery.Disruption.DockOutage(
                        request.dockCode(), new Interval(request.outageStart(), request.outageEnd()));
                record = Disruption.dockOutage(tenantId, request.scheduleDate(), request.dockCode(), request.outageStart(),
                        request.outageEnd(), decisionTime, snapshot.baseRunId(), user, now);
                outages = List.of(new ApplyRequest.BlockedInput(request.dockCode(), request.outageStart(), request.outageEnd()));
            }
            default -> throw ApiException.badRequest("Unknown disruption type.");
        }

        RecoveryOutcome outcome;
        try {
            outcome = planner.plan(problem, SnapshotMapping.current(snapshot), engineDisruption, decisionTime);
        } catch (IllegalArgumentException e) {
            // Unknown truck or dock, or a truck that is already loading: safe engine messages, no tenant data beyond the request.
            throw ApiException.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            throw ApiException.conflict("No valid recovery plan exists for this disruption.");
        }

        List<StoredPlan> stored = new java.util.ArrayList<>();
        int rank = 1;
        for (RecoveryPlan plan : outcome.plans()) {
            ApplyRequest apply = SnapshotMapping.applyRequest(snapshot, outcome.adjustedProblem(), plan.schedule(), outages,
                    "Recovery for disruption " + record.getId() + " (" + record.getType() + ")");
            stored.add(new StoredPlan(record.getId(), plan.strategy(), rank++, plan.moved(), plan.metrics().scheduled(),
                    plan.metrics().unscheduled(), plan.metrics().urgentUnscheduled(), plan.metrics().avgWaitMinutes(),
                    json.writeValueAsString(apply)));
        }

        tx.executeWithoutResult(status -> {
            disruptions.save(record);
            plans.saveAll(stored);
            audit.save(new AuditEntry(tenantId, record.getId(), "REPORTED", user, now, describe(record)));
            audit.save(new AuditEntry(tenantId, record.getId(), "PLANS_GENERATED", "system", now,
                    stored.size() + " valid plan(s); recommended " + stored.getFirst().getStrategy()
                            + " moving " + stored.getFirst().getMoved() + " appointment(s)"));
        });
        log.info("Disruption {} ({}) for tenant {} on {}: {} plan(s)", record.getId(), record.getType(), tenantId,
                record.getScheduleDate(), stored.size());
        return DisruptionResponse.of(record, stored, properties.adminApprovalMovedThreshold());
    }

    public DisruptionResponse get(String tenantId, UUID id) {
        Disruption d = find(tenantId, id);
        return DisruptionResponse.of(d, plans.findByDisruptionIdOrderByRank(id), properties.adminApprovalMovedThreshold());
    }

    public List<DisruptionResponse> recent(String tenantId) {
        return disruptions.findTop50ByTenantIdOrderByReportedAtDesc(tenantId).stream()
                .map(d -> DisruptionResponse.of(d, plans.findByDisruptionIdOrderByRank(d.getId()),
                        properties.adminApprovalMovedThreshold()))
                .toList();
    }

    public List<AuditEntry> auditTrail(String tenantId, UUID id) {
        find(tenantId, id);
        return audit.findByTenantIdAndDisruptionIdOrderBySeqAsc(tenantId, id);
    }

    /**
     * Applies one plan. Larger plans need an ADMIN here, and Dock Scheduling checks the same rule
     * again with the relayed token, along with every scheduling rule and the schedule version.
     */
    public DisruptionResponse approve(String tenantId, UUID id, String strategy, String note, String user, boolean isAdmin,
                                      String token) {
        Disruption d = find(tenantId, id);
        if (!d.isPending()) {
            throw ApiException.conflict("This disruption was already " + d.getStatus() + ".");
        }
        StoredPlan plan = plans.findByDisruptionIdOrderByRank(id).stream()
                .filter(p -> p.getStrategy().equals(strategy))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("No plan with strategy " + strategy + " for this disruption."));
        if (plan.getMoved() > properties.adminApprovalMovedThreshold() && !isAdmin) {
            tx.executeWithoutResult(s -> audit.save(new AuditEntry(tenantId, id, "APPROVAL_DENIED", user, clock.instant(),
                    strategy + " moves " + plan.getMoved() + " appointment(s); needs ADMIN")));
            throw ApiException.forbidden("This plan moves " + plan.getMoved() + " appointments; more than "
                    + properties.adminApprovalMovedThreshold() + " needs ADMIN approval.");
        }

        ApplyRequest request = json.readValue(plan.getApplyRequest(), ApplyRequest.class);
        AppliedSchedule applied;
        try {
            applied = dock.apply(d.getScheduleDate(), request, token);
        } catch (ApiException e) {
            if (e.getStatusCode().value() == 409) {
                // The world moved on: these plans can never be applied. Record that and close the disruption.
                tx.executeWithoutResult(s -> {
                    Disruption fresh = find(tenantId, id);
                    if (!fresh.isPending()) {
                        return;
                    }
                    fresh.markFailed(user, clock.instant(), "Schedule changed before approval");
                    disruptions.save(fresh);
                    audit.save(new AuditEntry(tenantId, id, "APPLY_FAILED", user, clock.instant(),
                            strategy + " rejected by Dock Scheduling: schedule changed or plan no longer valid"));
                });
            }
            throw e;
        }

        Instant now = clock.instant();
        tx.executeWithoutResult(s -> {
            Disruption fresh = find(tenantId, id);
            if (!java.util.Objects.equals(fresh.getVersion(), d.getVersion())) {
                throw ApiException.conflict("Someone else decided on this disruption at the same time.");
            }
            fresh.markApplied(user, now, strategy, applied.runId(), note);
            disruptions.save(fresh);
            audit.save(new AuditEntry(tenantId, id, "APPROVED", user, now, strategy + (note == null ? "" : ": " + note)));
            audit.save(new AuditEntry(tenantId, id, "APPLIED", "system", now,
                    strategy + " saved as schedule run " + applied.runId() + ", moving " + plan.getMoved() + " appointment(s)"));
        });
        return get(tenantId, id);
    }

    public DisruptionResponse reject(String tenantId, UUID id, String note, String user) {
        tx.executeWithoutResult(s -> {
            Disruption d = find(tenantId, id);
            if (!d.isPending()) {
                throw ApiException.conflict("This disruption was already " + d.getStatus() + ".");
            }
            Instant now = clock.instant();
            d.markRejected(user, now, note);
            disruptions.save(d);
            audit.save(new AuditEntry(tenantId, id, "REJECTED", user, now, note));
        });
        return get(tenantId, id);
    }

    private Disruption find(String tenantId, UUID id) {
        return disruptions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Disruption", id));
    }

    private static String describe(Disruption d) {
        return d.getType() == DisruptionType.LATE_ARRIVAL
                ? "Shipment " + d.getShipmentId() + " now arrives " + d.getNewArrival() + "; decision time " + d.getDecisionTime()
                : "Dock " + d.getDockCode() + " out " + d.getOutageStart() + " to " + d.getOutageEnd()
                        + "; decision time " + d.getDecisionTime();
    }
}
