package com.dockmind.scheduling;

import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Priority;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Small helpers so tests read like the scenario they describe. Times are minutes after 08:00. */
public final class TestData {

    public static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    public static final Interval ALL_DAY = new Interval(T0, T0.plus(Duration.ofHours(12)));

    private TestData() {
    }

    public static Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    public static Dock dock(String id, Capability... capabilities) {
        return Dock.open(id, capabilities.length == 0 ? Set.of() : Set.of(capabilities), ALL_DAY);
    }

    public static Job job(String id, int arrivalMinute, int loadingMinutes) {
        return new Job(id, Set.of(), Priority.STANDARD, at(arrivalMinute), Duration.ofMinutes(loadingMinutes), null);
    }

    public static Job job(String id, int arrivalMinute, int loadingMinutes, Priority priority, Capability... needs) {
        return new Job(id, needs.length == 0 ? Set.of() : Set.of(needs), priority, at(arrivalMinute),
                Duration.ofMinutes(loadingMinutes), null);
    }

    public static Job jobWithDeadline(String id, int arrivalMinute, int loadingMinutes, int latestStartMinute) {
        return new Job(id, Set.of(), Priority.STANDARD, at(arrivalMinute), Duration.ofMinutes(loadingMinutes),
                at(latestStartMinute));
    }
}
