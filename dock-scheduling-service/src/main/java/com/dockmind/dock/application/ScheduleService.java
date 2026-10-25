package com.dockmind.dock.application;

import com.dockmind.common.web.ApiException;
import com.dockmind.common.web.ResourceNotFoundException;
import com.dockmind.dock.api.ScheduleResponse;
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

    public ScheduleService(DockRepository docks, ShipmentViewRepository shipments, ScheduleRunRepository runs) {
        this.docks = docks;
        this.shipments = shipments;
        this.runs = runs;
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
