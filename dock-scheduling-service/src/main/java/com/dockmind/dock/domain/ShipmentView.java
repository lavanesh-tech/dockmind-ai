package com.dockmind.dock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * This service's own copy of a shipment, built only from shipment events. It holds just the
 * fields scheduling needs. This is how microservices share data without sharing a database:
 * the owner publishes facts, and consumers keep the parts they care about.
 */
@Entity
@Table(name = "shipment_view")
public class ShipmentView {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String reference;

    @Column(name = "pallet_count", nullable = false)
    private int palletCount;

    /** Comma-separated capability names, e.g. {@code "HAZMAT,REFRIGERATED"}; empty for none. */
    @Column(name = "required_capabilities", nullable = false)
    private String requiredCapabilities;

    @Column(nullable = false)
    private String priority;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(nullable = false)
    private String status;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    @Column(name = "last_event_id", nullable = false)
    private UUID lastEventId;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    protected ShipmentView() {
    }

    public ShipmentView(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }

    /** True if an event at {@code occurredAt} is newer than what this view already reflects. */
    public boolean isOlderThan(Instant occurredAt) {
        return lastEventAt == null || occurredAt.isAfter(lastEventAt);
    }

    public void apply(UUID eventId, Instant occurredAt, String tenantId, String reference, int palletCount,
                      List<String> capabilities, String priority, Instant windowStart, Instant windowEnd,
                      String status, Instant actualArrivalAt) {
        this.lastEventId = eventId;
        this.lastEventAt = occurredAt;
        this.tenantId = tenantId;
        this.reference = reference;
        this.palletCount = palletCount;
        this.requiredCapabilities = String.join(",", capabilities.stream().sorted().toList());
        this.priority = priority;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.status = status;
        this.actualArrivalAt = actualArrivalAt;
    }

    public List<String> requiredCapabilityNames() {
        return requiredCapabilities.isBlank() ? List.of() : Arrays.asList(requiredCapabilities.split(","));
    }

    public UUID getShipmentId() { return shipmentId; }
    public String getTenantId() { return tenantId; }
    public String getReference() { return reference; }
    public int getPalletCount() { return palletCount; }
    public String getPriority() { return priority; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public String getStatus() { return status; }
    public Instant getActualArrivalAt() { return actualArrivalAt; }
    public UUID getLastEventId() { return lastEventId; }
    public Instant getLastEventAt() { return lastEventAt; }
}
