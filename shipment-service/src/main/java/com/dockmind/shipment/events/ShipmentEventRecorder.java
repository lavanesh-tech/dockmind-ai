package com.dockmind.shipment.events;

import com.dockmind.common.web.CorrelationId;
import com.dockmind.shipment.domain.Shipment;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes an event to the outbox as part of the caller's transaction.
 *
 * <p>{@code Propagation.MANDATORY} makes this fail loudly if someone calls it outside a
 * transaction. That would break the whole point of the outbox: the event and the change must
 * commit or roll back together.
 */
@Component
public class ShipmentEventRecorder {

    private final OutboxRepository outbox;
    private final JsonMapper json;

    public ShipmentEventRecorder(OutboxRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(Shipment shipment, String eventType) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        EventEnvelope envelope = new EventEnvelope(eventId, eventType, ShipmentEvents.VERSION, shipment.getTenantId(),
                shipment.getId(), now, correlationId, ShipmentEventPayload.of(shipment));
        return outbox.save(new OutboxEvent(eventId, shipment.getTenantId(), shipment.getId(), eventType,
                ShipmentEvents.VERSION, json.writeValueAsString(envelope), correlationId, now));
    }
}
