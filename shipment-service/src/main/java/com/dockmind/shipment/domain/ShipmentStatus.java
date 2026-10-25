package com.dockmind.shipment.domain;

/** Lifecycle of a shipment. Allowed moves: BOOKED → ARRIVED, BOOKED → CANCELLED. */
public enum ShipmentStatus {
    BOOKED,
    ARRIVED,
    CANCELLED
}
