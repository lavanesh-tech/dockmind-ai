package com.dockmind.shipment.api;

import com.dockmind.shipment.domain.Direction;
import com.dockmind.shipment.domain.DockCapability;
import com.dockmind.shipment.domain.Priority;
import com.dockmind.shipment.domain.Shipment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/**
 * Body of {@code POST /api/v1/shipments}.
 *
 * <p>There is deliberately no tenant field: the tenant comes from the access token.
 * Bean Validation rejects bad input with a 400 that lists every invalid field, before any
 * business code runs.
 */
@Schema(description = "A shipment to book")
public record CreateShipmentRequest(

        @Schema(example = "PO-2026-1001")
        @NotBlank @Size(max = Shipment.MAX_REFERENCE_LENGTH)
        @Pattern(regexp = "^[A-Za-z0-9._/-]+$", message = "may contain only letters, digits and . _ / -")
        String reference,

        @NotNull
        Direction direction,

        @Min(1) @Max(Shipment.MAX_PALLETS)
        int palletCount,

        @Schema(description = "Equipment the dock must have; empty for none")
        Set<DockCapability> requiredCapabilities,

        @NotNull
        Priority priority,

        @Schema(example = "2026-11-02T08:00:00Z")
        @NotNull
        Instant windowStart,

        @Schema(example = "2026-11-02T10:00:00Z")
        @NotNull
        Instant windowEnd) {

    @AssertTrue(message = "windowEnd must be after windowStart")
    @Schema(hidden = true)
    public boolean isWindowEndAfterStart() {
        return windowStart == null || windowEnd == null || windowEnd.isAfter(windowStart);
    }
}
