package com.dockmind.dock.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/schedules/{date}/apply}: a complete proposed schedule (for example an
 * approved recovery plan). It is re-validated here against this service's own data before saving.
 *
 * @param baseRunId        the schedule version the plan was made from; must still be the latest
 * @param extraBlocked     dock outages the plan accounts for
 * @param arrivalOverrides trucks whose availability changed (late arrivals, decision time)
 */
public record ApplyScheduleRequest(
        @NotNull UUID baseRunId,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$") String label,
        @NotBlank @Size(max = 500) String reason,
        @NotNull List<@Valid AppointmentInput> assignments,
        @NotNull List<@Valid UnscheduledInput> unscheduled,
        List<@Valid BlockedInput> extraBlocked,
        List<@Valid ArrivalOverride> arrivalOverrides) {

    public record AppointmentInput(@NotNull UUID shipmentId, @NotBlank String dockCode, @NotNull Instant start, @NotNull Instant end) { }

    public record UnscheduledInput(@NotNull UUID shipmentId, @NotBlank String reason) { }

    public record BlockedInput(@NotBlank String dockCode, @NotNull Instant start, @NotNull Instant end) { }

    /** @param latestStart {@code null} removes the deadline (only allowed once the old one can't be met) */
    public record ArrivalOverride(@NotNull UUID shipmentId, @NotNull Instant availableFrom, Instant latestStart) { }
}
