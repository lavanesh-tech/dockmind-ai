package com.dockmind.scheduling.model;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One truck that needs a dock: a shipment as the scheduler sees it.
 *
 * @param id                   unique job identifier (the shipment ID)
 * @param requiredCapabilities equipment the dock must have
 * @param priority             urgency
 * @param arrival              when the truck is at the gate; loading cannot start earlier
 * @param loadingTime          how long the dock is occupied
 * @param latestStart          the latest acceptable start, or {@code null} for no deadline
 */
public record Job(String id, Set<Capability> requiredCapabilities, Priority priority,
                  Instant arrival, Duration loadingTime, Instant latestStart) {

    public Job {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(loadingTime, "loadingTime");
        if (loadingTime.isZero() || loadingTime.isNegative()) {
            throw new IllegalArgumentException("loadingTime must be positive for job " + id);
        }
        if (latestStart != null && latestStart.isBefore(arrival)) {
            throw new IllegalArgumentException("latestStart is before arrival for job " + id);
        }
        requiredCapabilities = requiredCapabilities == null || requiredCapabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(requiredCapabilities));
    }

    public Optional<Instant> deadline() {
        return Optional.ofNullable(latestStart);
    }

    /** Returns a copy with a new arrival time, e.g. after a late-arrival report. Keeps the same slack. */
    public Job arrivingAt(Instant newArrival) {
        Instant newLatest = latestStart == null
                ? null
                : newArrival.plus(Duration.between(arrival, latestStart));
        return new Job(id, requiredCapabilities, priority, newArrival, loadingTime, newLatest);
    }
}
