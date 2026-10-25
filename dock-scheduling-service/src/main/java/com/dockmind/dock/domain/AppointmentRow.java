package com.dockmind.dock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;
import java.util.UUID;

/** One shipment placed on one dock for one time slot, as saved in a schedule run. */
@Embeddable
public class AppointmentRow {

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(nullable = false)
    private String reference;

    @Column(name = "dock_code", nullable = false)
    private String dockCode;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    protected AppointmentRow() {
    }

    public AppointmentRow(UUID shipmentId, String reference, String dockCode, Instant startsAt, Instant endsAt) {
        this.shipmentId = shipmentId;
        this.reference = reference;
        this.dockCode = dockCode;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public UUID getShipmentId() { return shipmentId; }
    public String getReference() { return reference; }
    public String getDockCode() { return dockCode; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
}
