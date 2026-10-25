package com.dockmind.dock.api;

import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Priority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything needed to plan against the current schedule, in the engine's terms: docks with that
 * day's hours, trucks with availability, loading time and deadline, and the latest appointments.
 * {@code baseRunId} identifies the schedule version; applying a plan requires it to still be current.
 */
public record ScheduleSnapshot(LocalDate date, UUID baseRunId, int crewCapacity, List<SnapshotDock> docks,
                               List<SnapshotJob> jobs, List<ScheduleResponse.Appointment> appointments) {

    public record SnapshotDock(String code, List<Capability> capabilities, Instant opensAt, Instant closesAt) { }

    public record SnapshotJob(UUID shipmentId, String reference, List<Capability> requiredCapabilities, Priority priority,
                              Instant availableFrom, long loadingSeconds, Instant latestStart) { }
}
