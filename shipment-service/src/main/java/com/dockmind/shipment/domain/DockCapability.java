package com.dockmind.shipment.domain;

/** Equipment a shipment needs at its dock. A dock can serve a shipment only if it has all of them. */
public enum DockCapability {
    REFRIGERATED,
    HAZMAT,
    SIDE_LOAD,
    OVERSIZED
}
