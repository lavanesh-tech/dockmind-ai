package com.dockmind.recovery.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Priority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dock Scheduling's {@code GET /api/v1/schedules/{date}/snapshot} response (this service's own copy of the contract). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DockSnapshot(LocalDate date, UUID baseRunId, int crewCapacity, List<SnapshotDock> docks,
                           List<SnapshotJob> jobs, List<SnapshotAppointment> appointments) {

    public record SnapshotDock(String code, List<Capability> capabilities, Instant opensAt, Instant closesAt) { }

    public record SnapshotJob(UUID shipmentId, String reference, List<Capability> requiredCapabilities, Priority priority,
                              Instant availableFrom, long loadingSeconds, Instant latestStart) { }

    public record SnapshotAppointment(UUID shipmentId, String reference, String dockCode, Instant start, Instant end) { }
}
