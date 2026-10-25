package com.dockmind.dock.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body of {@code POST /api/v1/schedules/{date}/compute}. */
public record ComputeScheduleRequest(
        @Schema(description = "Loading crews available: at most this many docks load at once", example = "6")
        @Min(1) @Max(50) int crewCapacity) {
}
