package com.dockmind.scheduling.model;

/** Job urgency. Declaration order matters: later constants are more urgent. */
public enum Priority {
    STANDARD,
    EXPEDITED,
    CRITICAL;

    public boolean isUrgent() {
        return this != STANDARD;
    }
}
