package com.dockmind.dock.events;

import com.dockmind.dock.domain.ProcessedEvent;
import com.dockmind.dock.domain.ProcessedEventRepository;
import com.dockmind.dock.domain.ShipmentView;
import com.dockmind.dock.domain.ShipmentViewRepository;
import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Applies one shipment event to the local shipment view, exactly once.
 *
 * <ol>
 *   <li><b>Validate</b>: unknown versions or missing fields are rejected for good (dead-letter).</li>
 *   <li><b>Deduplicate</b>: if the event ID was already processed, do nothing. Kafka
 *       delivers at least once, so duplicates are normal, not errors.</li>
 *   <li><b>Ignore stale events</b>: if the view already reflects a newer event, an older one
 *       arriving late must not overwrite it.</li>
 *   <li><b>Apply</b> and record the event ID, in one transaction, so a crash between the two
 *       can't happen.</li>
 * </ol>
 */
@Component
public class ShipmentEventHandler {

    public static final int SUPPORTED_VERSION = 1;
    private static final Set<String> STATUSES = Set.of("BOOKED", "ARRIVED", "CANCELLED");
    private static final Logger log = LoggerFactory.getLogger(ShipmentEventHandler.class);

    private final JsonMapper json;
    private final ProcessedEventRepository processed;
    private final ShipmentViewRepository views;

    public ShipmentEventHandler(JsonMapper json, ProcessedEventRepository processed, ShipmentViewRepository views) {
        this.json = json;
        this.processed = processed;
        this.views = views;
    }

    public enum Outcome { APPLIED, DUPLICATE, STALE }

    @Transactional
    public Outcome handle(String message) {
        IncomingShipmentEvent event = parse(message);

        if (processed.existsById(event.eventId())) {
            log.info("Skipping duplicate event {}", event.eventId());
            return Outcome.DUPLICATE;
        }
        processed.save(new ProcessedEvent(event.eventId(), event.eventType(), Instant.now()));

        var p = event.payload();
        ShipmentView view = views.findById(p.shipmentId()).orElseGet(() -> new ShipmentView(p.shipmentId()));
        if (!view.isOlderThan(event.occurredAt())) {
            log.info("Ignoring stale event {} for shipment {}", event.eventId(), p.shipmentId());
            return Outcome.STALE;
        }
        view.apply(event.eventId(), event.occurredAt(), event.tenantId(), p.reference(), p.palletCount(),
                p.requiredCapabilities() == null ? List.of() : p.requiredCapabilities(), p.priority(),
                p.windowStart(), p.windowEnd(), p.status(), p.actualArrivalAt());
        views.save(view);
        return Outcome.APPLIED;
    }

    IncomingShipmentEvent parse(String message) {
        IncomingShipmentEvent event;
        try {
            event = json.readValue(message, IncomingShipmentEvent.class);
        } catch (RuntimeException e) { // Jackson 3 exceptions are unchecked
            throw new UnsupportedEventException("Not a valid shipment event: " + e.getClass().getSimpleName());
        }
        if (event.eventVersion() != SUPPORTED_VERSION) {
            throw new UnsupportedEventException("Unsupported eventVersion " + event.eventVersion());
        }
        var p = event.payload();
        if (event.eventId() == null || event.tenantId() == null || event.occurredAt() == null || p == null
                || p.shipmentId() == null || p.reference() == null || p.windowStart() == null
                || p.windowEnd() == null || p.priority() == null || p.status() == null) {
            throw new UnsupportedEventException("Event is missing required fields");
        }
        if (!STATUSES.contains(p.status())) {
            throw new UnsupportedEventException("Unknown status " + p.status());
        }
        try {
            Priority.valueOf(p.priority());
            if (p.requiredCapabilities() != null) {
                p.requiredCapabilities().forEach(Capability::valueOf);
            }
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEventException("Unknown priority or capability");
        }
        return event;
    }
}
