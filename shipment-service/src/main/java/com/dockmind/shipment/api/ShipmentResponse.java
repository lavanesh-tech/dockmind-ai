package com.dockmind.shipment.api;

import com.dockmind.shipment.domain.Direction;
import com.dockmind.shipment.domain.DockCapability;
import com.dockmind.shipment.domain.Priority;
import com.dockmind.shipment.domain.Shipment;
import com.dockmind.shipment.domain.ShipmentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the API returns for a shipment. A separate type from the entity, so database changes
 * don't silently change the public contract, and internal fields (tenant, timestamps) aren't exposed.
 *
 * @param late    whether the truck arrived after its window closed ({@code null} until it arrives)
 * @param version the optimistic-locking version, so clients can see that a record changed
 */
public record ShipmentResponse(UUID id, String reference, Direction direction, int palletCount,
                               List<DockCapability> requiredCapabilities, Priority priority,
                               Instant windowStart, Instant windowEnd, ShipmentStatus status,
                               Instant actualArrivalAt, Boolean late, long version) {

    static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(
                s.getId(), s.getReference(), s.getDirection(), s.getPalletCount(),
                s.getRequiredCapabilities().stream().sorted().toList(), // sorted: stable JSON
                s.getPriority(), s.getWindowStart(), s.getWindowEnd(), s.getStatus(),
                s.getActualArrivalAt(),
                s.getActualArrivalAt() == null ? null : s.isLate(s.getActualArrivalAt()),
                s.getVersion() == null ? 0 : s.getVersion());
    }
}
