package com.dockmind.recovery.api;

import com.dockmind.recovery.domain.DisruptionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A disruption report. LATE_ARRIVAL needs {@code shipmentId} and {@code newArrival};
 * DOCK_OUTAGE needs {@code dockCode}, {@code outageStart} and {@code outageEnd}.
 *
 * @param decisionTime when the plan takes effect; appointments that started before it stay fixed.
 *                     Defaults to now.
 */
public record ReportDisruptionRequest(
        @NotNull DisruptionType type,
        @NotNull LocalDate scheduleDate,
        UUID shipmentId,
        Instant newArrival,
        @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{0,39}$") String dockCode,
        Instant outageStart,
        Instant outageEnd,
        Instant decisionTime) {
}
