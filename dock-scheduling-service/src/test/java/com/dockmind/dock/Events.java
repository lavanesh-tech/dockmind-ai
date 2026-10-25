package com.dockmind.dock;

import java.time.Instant;
import java.util.UUID;

/** Builds shipment-event JSON exactly as the shipment service publishes it (docs/EVENTS.md). */
public final class Events {

    private Events() {
    }

    public static String shipment(UUID eventId, String type, String tenant, UUID shipmentId, Instant occurredAt,
                                  String reference, int pallets, String capabilitiesJson, String priority,
                                  String windowStart, String windowEnd, String status, String arrivedAt) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"tenantId":"%s","aggregateId":"%s",
                 "occurredAt":"%s","correlationId":"test-corr","payload":{
                 "shipmentId":"%s","reference":"%s","direction":"INBOUND","palletCount":%d,
                 "requiredCapabilities":%s,"priority":"%s","windowStart":"%s","windowEnd":"%s",
                 "status":"%s","actualArrivalAt":%s}}"""
                .formatted(eventId, type, tenant, shipmentId, occurredAt, shipmentId, reference, pallets,
                        capabilitiesJson, priority, windowStart, windowEnd, status,
                        arrivedAt == null ? "null" : "\"" + arrivedAt + "\"");
    }

    public static String booked(String tenant, UUID shipmentId, String reference, String capabilitiesJson,
                                String priority, String windowStart, String windowEnd) {
        return shipment(UUID.randomUUID(), "ShipmentBooked", tenant, shipmentId, Instant.now(), reference, 20,
                capabilitiesJson, priority, windowStart, windowEnd, "BOOKED", null);
    }

    /** A fresh, valid tenant ID, so tests sharing one database and broker don't see each other's data. */
    public static String newTenant() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
