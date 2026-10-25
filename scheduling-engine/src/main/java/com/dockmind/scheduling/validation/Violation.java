package com.dockmind.scheduling.validation;

/** One broken rule in a schedule. */
public record Violation(String jobId, Rule rule, String detail) {

    public enum Rule {
        UNKNOWN_JOB,
        UNKNOWN_DOCK,
        DUPLICATE_JOB,
        MISSING_JOB,
        WRONG_DURATION,
        CAPABILITY_MISMATCH,
        BEFORE_ARRIVAL,
        AFTER_LATEST_START,
        OUTSIDE_OPEN_HOURS,
        DOCK_BLOCKED,
        DOCK_OVERLAP,
        CREW_CAPACITY_EXCEEDED
    }
}
