package com.dockmind.shipment.events;

/**
 * The shipment event contract (see docs/EVENTS.md). Consumers depend on these names, so they
 * change only by adding a new {@code eventVersion}, never by editing an existing one.
 */
public final class ShipmentEvents {

    /** Kafka topic. Key = shipment ID, so all events for one shipment stay in order. */
    public static final String TOPIC = "shipment-events";

    public static final String BOOKED = "ShipmentBooked";
    public static final String ARRIVED = "ShipmentArrived";
    public static final String CANCELLED = "ShipmentCancelled";

    /** Current envelope and payload version for all three types. */
    public static final int VERSION = 1;

    /** Kafka header carrying the request's correlation ID, so traces continue across services. */
    public static final String CORRELATION_HEADER = "correlationId";

    private ShipmentEvents() {
    }
}
