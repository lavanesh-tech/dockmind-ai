package com.dockmind.shipment.events;

import com.dockmind.shipment.domain.Shipment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The shipment state carried by every shipment event: a full snapshot, not a diff, so a
 * consumer can rebuild its view from any single event and never needs to call back.
 */
public record ShipmentEventPayload(UUID shipmentId, String reference, String direction, int palletCount,
                                   List<String> requiredCapabilities, String priority,
                                   Instant windowStart, Instant windowEnd, String status,
                                   Instant actualArrivalAt) {

    static ShipmentEventPayload of(Shipment s) {
        return new ShipmentEventPayload(s.getId(), s.getReference(), s.getDirection().name(), s.getPalletCount(),
                s.getRequiredCapabilities().stream().map(Enum::name).sorted().toList(),
                s.getPriority().name(), s.getWindowStart(), s.getWindowEnd(), s.getStatus().name(),
                s.getActualArrivalAt());
    }
}
