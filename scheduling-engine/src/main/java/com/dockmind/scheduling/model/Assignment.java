package com.dockmind.scheduling.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

/** A job placed on a dock for a time slot. */
public record Assignment(String jobId, String dockId, Instant start, Instant end) {

    /** Stable order used everywhere a schedule is printed or compared. */
    public static final Comparator<Assignment> ORDER = Comparator.comparing(Assignment::start)
            .thenComparing(Assignment::dockId)
            .thenComparing(Assignment::jobId);

    public Interval slot() {
        return new Interval(start, end);
    }

    public Duration waitFrom(Instant arrival) {
        return Duration.between(arrival, start);
    }
}
