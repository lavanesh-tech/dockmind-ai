package com.dockmind.dock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/** A shipment the scheduler could not place, and why. */
@Embeddable
public class UnscheduledRow {

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false)
    private String reason;

    protected UnscheduledRow() {
    }

    public UnscheduledRow(UUID shipmentId, String reference, String reason) {
        this.shipmentId = shipmentId;
        this.reference = reference;
        this.reason = reason;
    }

    public UUID getShipmentId() { return shipmentId; }
    public String getReference() { return reference; }
    public String getReason() { return reason; }
}
