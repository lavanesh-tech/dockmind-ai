package com.dockmind.shipment.domain;

/**
 * Scheduling priority. The scheduler (Milestone 2) serves higher priorities first when
 * docks are contended. Declaration order matters: later constants are more urgent.
 */
public enum Priority {
    STANDARD,
    EXPEDITED,
    CRITICAL
}
