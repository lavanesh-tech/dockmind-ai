package com.dockmind.dock.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The shipment event contract as this service reads it (see docs/EVENTS.md). It is the
 * consumer's own copy of the contract: services share the message format, not Java classes,
 * so each can be built and deployed independently. Unknown fields are ignored.
 */
public record IncomingShipmentEvent(UUID eventId, String eventType, int eventVersion, String tenantId,
                                    UUID aggregateId, Instant occurredAt, String correlationId, Payload payload) {

    public record Payload(UUID shipmentId, String reference, String direction, int palletCount,
                          List<String> requiredCapabilities, String priority, Instant windowStart,
                          Instant windowEnd, String status, Instant actualArrivalAt) {
    }
}
