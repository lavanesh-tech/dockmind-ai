package com.dockmind.shipment.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The outer shape of every event DockMind publishes.
 *
 * @param eventId       unique per event; consumers record it to ignore redeliveries (idempotency)
 * @param eventType     e.g. {@code ShipmentBooked}
 * @param eventVersion  schema version of this type; consumers reject versions they don't understand
 * @param tenantId      owning tenant, from the verified token of the request that caused the event
 * @param aggregateId   the shipment the event is about
 * @param occurredAt    when the change happened; consumers ignore events older than what they've applied
 * @param correlationId the request that caused it, for tracing across services
 */
public record EventEnvelope(UUID eventId, String eventType, int eventVersion, String tenantId, UUID aggregateId,
                            Instant occurredAt, String correlationId, ShipmentEventPayload payload) {
}
