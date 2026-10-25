package com.dockmind.scheduling.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A half-open time interval {@code [start, end)}: it includes {@code start} but not
 * {@code end}. With half-open intervals, a job ending at 10:00 and another starting at
 * 10:00 do not overlap, which is exactly the rule a dock needs.
 */
public record Interval(Instant start, Instant end) implements Comparable<Interval> {

    public Interval {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Interval end must be after start: " + start + " .. " + end);
        }
    }

    public static Interval of(Instant start, Duration length) {
        return new Interval(start, start.plus(length));
    }

    public boolean overlaps(Interval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(Interval other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    /** Orders by start, then by end, so sorted lists are deterministic. */
    @Override
    public int compareTo(Interval other) {
        int byStart = start.compareTo(other.start);
        return byStart != 0 ? byStart : end.compareTo(other.end);
    }
}
