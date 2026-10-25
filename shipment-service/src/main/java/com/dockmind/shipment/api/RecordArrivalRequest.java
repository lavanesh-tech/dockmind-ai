package com.dockmind.shipment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Body of {@code POST /api/v1/shipments/{id}/arrival}. */
public record RecordArrivalRequest(
        @Schema(example = "2026-11-02T08:20:00Z")
        @NotNull
        Instant arrivedAt) {
}
