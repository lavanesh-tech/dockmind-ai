package com.dockmind.recovery.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Body of Dock Scheduling's {@code POST /api/v1/schedules/{date}/apply}. */
public record ApplyRequest(UUID baseRunId, String label, String reason, List<AppointmentInput> assignments,
                           List<UnscheduledInput> unscheduled, List<BlockedInput> extraBlocked,
                           List<ArrivalOverride> arrivalOverrides) {

    public record AppointmentInput(UUID shipmentId, String dockCode, Instant start, Instant end) { }

    public record UnscheduledInput(UUID shipmentId, String reason) { }

    public record BlockedInput(String dockCode, Instant start, Instant end) { }

    public record ArrivalOverride(UUID shipmentId, Instant availableFrom, Instant latestStart) { }
}
