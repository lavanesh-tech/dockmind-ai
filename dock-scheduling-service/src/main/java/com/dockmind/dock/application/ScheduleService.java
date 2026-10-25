package com.dockmind.dock.application;

import com.dockmind.common.web.ApiException;
import com.dockmind.common.web.ResourceNotFoundException;
import com.dockmind.dock.api.ApplyScheduleRequest;
import com.dockmind.dock.api.ScheduleResponse;
import com.dockmind.dock.api.ScheduleSnapshot;
import com.dockmind.dock.domain.AppointmentRow;
import com.dockmind.dock.domain.DockRepository;
import com.dockmind.dock.domain.ScheduleRun;
import com.dockmind.dock.domain.ScheduleRunRepository;
import com.dockmind.dock.domain.ShipmentView;
import com.dockmind.dock.domain.ShipmentViewRepository;
import com.dockmind.dock.domain.UnscheduledRow;
import com.dockmind.scheduling.engine.DispatchScheduler;
import com.dockmind.scheduling.engine.Scheduler;
import com.dockmind.scheduling.metrics.ScheduleMetrics;
import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.validation.ScheduleValidator;
import com.dockmind.scheduling.validation.Violation;
import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Unscheduled;
import com.dockmind.scheduling.recovery.RecoveryPlanner;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes a day's schedule for one tenant.
 *
 * <p>It reads only this service's own data (docks + the shipment view built from events),
 * runs the deterministic {@link DispatchScheduler}, and <b>refuses to save</b> anything the
 * independent {@link ScheduleValidator} rejects.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final Set<String> NEEDS_A_DOCK = Set.of("BOOKED", "ARRIVED");

    private final DockRepository docks;
    private final ShipmentViewRepository shipments;
    private final ScheduleRunRepository runs;
    private final Scheduler scheduler = new DispatchScheduler();
    private final int adminApprovalMovedThreshold;

    public ScheduleService(DockRepository docks, ShipmentViewRepository shipments, ScheduleRunRepository runs,
                           @Value("${dockmind.recovery.admin-approval-moved-threshold:3}") int adminApprovalMovedThreshold) {
        this.docks = docks;
        this.shipments = shipments;
        this.runs = runs;
        this.adminApprovalMovedThreshold = adminApprovalMovedThreshold;
    }

    /** The current schedule in engine terms, for planners such as Disruption Recovery. */
    @Transactional(readOnly = true)
    public ScheduleSnapshot snapshot(String tenantId, LocalDate date) {
        ScheduleRun run = runs.findFirstByTenantIdAndScheduleDateOrderByComputedAtDesc(tenantId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule for", date));
        SchedulingProblem problem = problemFor(tenantId, date, run.getCrewCapacity());
        Map<String, ShipmentView> views = pendingById(tenantId, date);
        return new ScheduleSnapshot(date, run.getId(), run.getCrewCapacity(),
                problem.docks().stream().map(d -> new ScheduleSnapshot.SnapshotDock(d.id(),
                        d.capabilities().stream().sorted().toList(), d.openHours().start(), d.openHours().end())).toList(),
                problem.jobs().stream().map(j -> new ScheduleSnapshot.SnapshotJob(UUID.fromString(j.id()),
                        views.get(j.id()).getReference(), j.requiredCapabilities().stream().sorted().toList(), j.priority(),
                        j.arrival(), j.loadingTime().toSeconds(), j.latestStart())).toList(),
                ScheduleResponse.from(run).appointments().stream()
                        .filter(a -> views.containsKey(a.shipmentId().toString())).toList());
    }

    /**
     * Saves a proposed schedule after checking it independently: it must be based on the latest
     * schedule version, pass the validator against this service's data, and, if it moves many
     * appointments, come from an ADMIN. This is the gate no planner (human, recovery or AI) can bypass.
     */
    @Transactional
    public ScheduleResponse apply(String tenantId, LocalDate date, ApplyScheduleRequest request, String requestedBy,
                                  boolean isAdmin) {
        ScheduleRun base = runs.findFirstByTenantIdAndScheduleDateOrderByComputedAtDesc(tenantId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule for", date));
        if (!base.getId().equals(request.baseRunId())) {
            throw ApiException.conflict("The schedule changed after this plan was made. Generate a new plan.");
        }
        SchedulingProblem problem = withOverrides(problemFor(tenantId, date, base.getCrewCapacity()), request);

        List<Assignment> assignments = request.assignments().stream()
                .map(a -> new Assignment(a.shipmentId().toString(), a.dockCode(), a.start(), a.end())).toList();
        List<Unscheduled> unscheduled = new ArrayList<>();
        for (var u : request.unscheduled()) {
            try {
                unscheduled.add(new Unscheduled(u.shipmentId().toString(), Unscheduled.Reason.valueOf(u.reason())));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown unscheduled reason " + u.reason());
            }
        }
        Schedule proposed = new Schedule(request.label(), assignments, unscheduled);

        List<Violation> violations = ScheduleValidator.validate(problem, proposed);
        if (!violations.isEmpty()) {
            throw ApiException.conflict("The plan breaks " + violations.size() + " scheduling rule(s), first: "
                    + violations.getFirst().rule() + ". Generate a new plan.");
        }
        int moved = RecoveryPlanner.moved(asSchedule(base), proposed);
        if (moved > adminApprovalMovedThreshold && !isAdmin) {
            throw ApiException.forbidden("This plan moves " + moved + " appointments; more than "
                    + adminApprovalMovedThreshold + " needs ADMIN approval.");
        }

        Map<UUID, ShipmentView> byId = new HashMap<>();
        pendingById(tenantId, date).forEach((k, v) -> byId.put(UUID.fromString(k), v));
        ScheduleRun run = runs.save(new ScheduleRun(tenantId, date, request.label(), base.getCrewCapacity(), Instant.now(),
                requestedBy, ScheduleMetrics.of(problem, proposed).avgWaitMinutes(),
                proposed.assignments().stream().map(a -> new AppointmentRow(UUID.fromString(a.jobId()),
                        byId.get(UUID.fromString(a.jobId())).getReference(), a.dockId(), a.start(), a.end())).toList(),
                proposed.unscheduled().stream().map(u -> new UnscheduledRow(UUID.fromString(u.jobId()),
                        byId.get(UUID.fromString(u.jobId())).getReference(), u.reason().name())).toList()));
        log.info("Applied schedule '{}' for tenant {} on {} by {} (moved {}): {}", request.label(), tenantId, date,
                requestedBy, moved, request.reason());
        return ScheduleResponse.from(run);
    }

    private SchedulingProblem problemFor(String tenantId, LocalDate date, int crewCapacity) {
        List<com.dockmind.dock.domain.Dock> tenantDocks = docks.findByTenantIdOrderByCode(tenantId);
        return new SchedulingProblem(tenantDocks.stream().map(d -> toEngineDock(d, date)).toList(),
                pendingById(tenantId, date).values().stream().map(ScheduleService::toJob)
                        .sorted(java.util.Comparator.comparing(Job::id)).toList(),
                crewCapacity);
    }

    private Map<String, ShipmentView> pendingById(String tenantId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<String, ShipmentView> map = new java.util.TreeMap<>();
        shipments.findByTenantIdAndStatusInAndWindowStartGreaterThanEqualAndWindowStartLessThan(tenantId, NEEDS_A_DOCK, dayStart, dayEnd)
                .forEach(v -> map.put(v.getShipmentId().toString(), v));
        return map;
    }

    /**
     * Applies the plan's stated outages and availability changes, but only changes that make the
     * problem stricter or follow from it: a truck can only become available later, and a deadline can
     * only be dropped once it can no longer be met. A plan can't loosen the rules it is checked against.
     */
    static SchedulingProblem withOverrides(SchedulingProblem problem, ApplyScheduleRequest request) {
        Map<String, Dock> docks = new java.util.LinkedHashMap<>(problem.docksById());
        if (request.extraBlocked() != null) {
            for (var b : request.extraBlocked()) {
                Dock d = docks.get(b.dockCode());
                if (d == null || !b.end().isAfter(b.start())) {
                    throw ApiException.badRequest("Invalid outage for dock " + b.dockCode());
                }
                docks.put(d.id(), d.withBlocked(new Interval(b.start(), b.end())));
            }
        }
        Map<String, Job> jobs = new java.util.LinkedHashMap<>(problem.jobsById());
        if (request.arrivalOverrides() != null) {
            for (var o : request.arrivalOverrides()) {
                Job j = jobs.get(o.shipmentId().toString());
                if (j == null || o.availableFrom().isBefore(j.arrival())) {
                    throw ApiException.badRequest("Invalid availability change for shipment " + o.shipmentId());
                }
                boolean deadlineKept = java.util.Objects.equals(o.latestStart(), j.latestStart());
                boolean deadlineLegitimatelyDropped = o.latestStart() == null && j.latestStart() != null
                        && j.latestStart().isBefore(o.availableFrom());
                if (!deadlineKept && !deadlineLegitimatelyDropped) {
                    throw ApiException.badRequest("Invalid deadline change for shipment " + o.shipmentId());
                }
                jobs.put(j.id(), new Job(j.id(), j.requiredCapabilities(), j.priority(), o.availableFrom(), j.loadingTime(),
                        o.latestStart()));
            }
        }
        return new SchedulingProblem(List.copyOf(docks.values()), List.copyOf(jobs.values()), problem.crewCapacity());
    }

    private static Schedule asSchedule(ScheduleRun run) {
        return new Schedule(run.getAlgorithm(),
                run.getAppointments().stream().map(a -> new Assignment(a.getShipmentId().toString(), a.getDockCode(),
                        a.getStartsAt(), a.getEndsAt())).toList(),
                run.getUnscheduled().stream().map(u -> new Unscheduled(u.getShipmentId().toString(),
                        Unscheduled.Reason.valueOf(u.getReason()))).toList());
    }

    @Transactional
    public ScheduleResponse compute(String tenantId, LocalDate date, int crewCapacity, String requestedBy) {
        List<com.dockmind.dock.domain.Dock> tenantDocks = docks.findByTenantIdOrderByCode(tenantId);
        if (tenantDocks.isEmpty()) {
            throw ApiException.badRequest("Register at least one dock before computing a schedule.");
        }
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<ShipmentView> pending = shipments
                .findByTenantIdAndStatusInAndWindowStartGreaterThanEqualAndWindowStartLessThan(
                        tenantId, NEEDS_A_DOCK, dayStart, dayEnd);

        SchedulingProblem problem = new SchedulingProblem(
                tenantDocks.stream().map(d -> toEngineDock(d, date)).toList(),
                pending.stream().map(ScheduleService::toJob).toList(),
                crewCapacity);
        Schedule schedule = scheduler.schedule(problem);

        List<Violation> violations = ScheduleValidator.validate(problem, schedule);
        if (!violations.isEmpty()) {
            // Should be impossible; if it happens, never persist or show an invalid plan.
            log.error("Scheduler produced {} violations for tenant {} on {}: {}", violations.size(), tenantId, date, violations);
            throw new IllegalStateException("Refusing to save an invalid schedule");
        }

        Map<UUID, ShipmentView> byId = pending.stream().collect(Collectors.toMap(ShipmentView::getShipmentId, Function.identity()));
        List<AppointmentRow> appointments = schedule.assignments().stream()
                .map(a -> {
                    UUID id = UUID.fromString(a.jobId());
                    return new AppointmentRow(id, byId.get(id).getReference(), a.dockId(), a.start(), a.end());
                })
                .toList();
        List<UnscheduledRow> unscheduled = schedule.unscheduled().stream()
                .map(u -> {
                    UUID id = UUID.fromString(u.jobId());
                    return new UnscheduledRow(id, byId.get(id).getReference(), u.reason().name());
                })
                .toList();

        ScheduleRun run = runs.save(new ScheduleRun(tenantId, date, scheduler.name(), crewCapacity, Instant.now(),
                requestedBy, ScheduleMetrics.of(problem, schedule).avgWaitMinutes(), appointments, unscheduled));
        return ScheduleResponse.from(run);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse latest(String tenantId, LocalDate date) {
        return runs.findFirstByTenantIdAndScheduleDateOrderByComputedAtDesc(tenantId, date)
                .map(ScheduleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule for", date));
    }

    static Dock toEngineDock(com.dockmind.dock.domain.Dock dock, LocalDate date) {
        Instant opens = date.atTime(dock.getOpensAt()).toInstant(ZoneOffset.UTC);
        Instant closes = date.atTime(dock.getClosesAt()).toInstant(ZoneOffset.UTC);
        return Dock.open(dock.getCode(), dock.getCapabilities(), new Interval(opens, closes));
    }

    /**
     * A shipment as the scheduler sees it. The truck is available from its actual arrival if it
     * has arrived, otherwise from the start of its window. It must start before its window ends;
     * a truck that already arrived after its window has no deadline left, only "as soon as possible".
     */
    static Job toJob(ShipmentView s) {
        Instant available = s.getActualArrivalAt() != null ? s.getActualArrivalAt() : s.getWindowStart();
        Instant latestStart = s.getWindowEnd().isAfter(available) ? s.getWindowEnd() : null;
        Set<Capability> needs = EnumSet.noneOf(Capability.class);
        s.requiredCapabilityNames().forEach(name -> needs.add(Capability.valueOf(name)));
        return new Job(s.getShipmentId().toString(), needs, Priority.valueOf(s.getPriority()), available,
                LoadingTimes.forPallets(s.getPalletCount()), latestStart);
    }
}
