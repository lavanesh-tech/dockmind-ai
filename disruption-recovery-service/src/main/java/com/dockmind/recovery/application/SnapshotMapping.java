package com.dockmind.recovery.application;

import com.dockmind.recovery.client.ApplyRequest;
import com.dockmind.recovery.client.DockSnapshot;
import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Converts between Dock Scheduling's wire format and the engine's model. Pure functions. */
final class SnapshotMapping {

    private SnapshotMapping() {
    }

    static SchedulingProblem problem(DockSnapshot s) {
        return new SchedulingProblem(
                s.docks().stream().map(d -> new Dock(d.code(),
                        caps(d.capabilities()),
                        new Interval(d.opensAt(), d.closesAt()), List.of())).toList(),
                s.jobs().stream().map(j -> new Job(j.shipmentId().toString(),
                        caps(j.requiredCapabilities()),
                        j.priority(), j.availableFrom(), Duration.ofSeconds(j.loadingSeconds()), j.latestStart())).toList(),
                s.crewCapacity());
    }

    static Schedule current(DockSnapshot s) {
        return new Schedule("CURRENT",
                s.appointments().stream().map(a -> new Assignment(a.shipmentId().toString(), a.dockCode(), a.start(), a.end())).toList(),
                List.of());
    }

    /**
     * The request Dock Scheduling needs to apply a plan: the full schedule, plus every change to the
     * problem the plan relies on (an outage, later availability), so it can re-validate independently.
     */
    static ApplyRequest applyRequest(DockSnapshot snapshot, SchedulingProblem adjusted, Schedule plan,
                                     List<ApplyRequest.BlockedInput> outages, String reason) {
        Map<String, DockSnapshot.SnapshotJob> original = snapshot.jobs().stream()
                .collect(Collectors.toMap(j -> j.shipmentId().toString(), Function.identity()));
        List<ApplyRequest.ArrivalOverride> overrides = new ArrayList<>();
        for (Job job : adjusted.jobs()) {
            DockSnapshot.SnapshotJob before = original.get(job.id());
            if (!job.arrival().equals(before.availableFrom()) || !Objects.equals(job.latestStart(), before.latestStart())) {
                overrides.add(new ApplyRequest.ArrivalOverride(UUID.fromString(job.id()), job.arrival(), job.latestStart()));
            }
        }
        return new ApplyRequest(snapshot.baseRunId(), plan.algorithm(), reason,
                plan.assignments().stream().map(a -> new ApplyRequest.AppointmentInput(UUID.fromString(a.jobId()),
                        a.dockId(), a.start(), a.end())).toList(),
                plan.unscheduled().stream().map(u -> new ApplyRequest.UnscheduledInput(UUID.fromString(u.jobId()),
                        u.reason().name())).toList(),
                outages, overrides);
    }

    private static Set<Capability> caps(List<Capability> list) {
        return list == null || list.isEmpty() ? Set.of() : EnumSet.copyOf(list);
    }
}
