package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.SchedulingProblem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The mutable booking state while a scheduler works: what each dock is busy with, and how
 * many crews are in use at any moment. It answers one question: "what is the earliest time
 * this job could start on this dock?", and records bookings.
 *
 * <p><b>Why the candidate times are enough.</b> A slot can only become feasible when something
 * that blocked it ends: a booking on the dock, an outage, or another dock's booking that was
 * using a crew. So the earliest feasible start is either the job's own earliest time or the
 * end of one of those intervals. Checking those candidates in time order and taking the first
 * that works gives the exact earliest start, without scanning minute by minute.
 *
 * <p>Complexity: O(n) candidates per query, each checked in O(n), so O(n²) per job and dock
 * in the worst case. That is fast for a warehouse day (hundreds of jobs); the benchmark
 * reports the measured time.
 */
final class Timeline {

    private final int crewCapacity;
    private final Map<String, List<Interval>> dockBusy = new HashMap<>();
    private final List<Interval> crewBusy = new ArrayList<>();

    Timeline(SchedulingProblem problem) {
        this.crewCapacity = problem.crewCapacity();
        for (Dock dock : problem.docks()) {
            dockBusy.put(dock.id(), new ArrayList<>(dock.blocked()));
        }
    }

    /** The earliest start for {@code job} on {@code dock} given current bookings, or empty if none fits. */
    Optional<Instant> earliestStart(Dock dock, Job job) {
        if (!dock.canServe(job)) {
            return Optional.empty();
        }
        Instant earliest = later(job.arrival(), dock.openHours().start());
        List<Interval> busyOnDock = dockBusy.get(dock.id());

        TreeSet<Instant> candidates = new TreeSet<>();
        candidates.add(earliest);
        addEndsAfter(busyOnDock, earliest, candidates);
        addEndsAfter(crewBusy, earliest, candidates);

        for (Instant start : candidates) {
            if (job.latestStart() != null && start.isAfter(job.latestStart())) {
                return Optional.empty(); // candidates only get later from here
            }
            Interval slot = Interval.of(start, job.loadingTime());
            if (slot.end().isAfter(dock.openHours().end())) {
                return Optional.empty();
            }
            if (overlapsAny(busyOnDock, slot)) {
                continue;
            }
            if (maxConcurrent(crewBusy, slot) >= crewCapacity) {
                continue;
            }
            return Optional.of(start);
        }
        return Optional.empty();
    }

    /** The first moment after {@code t} when a booking, an outage or a crew assignment ends. */
    Optional<Instant> nextReleaseAfter(Instant t) {
        Instant next = null;
        for (List<Interval> intervals : dockBusy.values()) {
            next = earliestEndAfter(intervals, t, next);
        }
        next = earliestEndAfter(crewBusy, t, next);
        return Optional.ofNullable(next);
    }

    private static Instant earliestEndAfter(List<Interval> intervals, Instant t, Instant current) {
        Instant best = current;
        for (Interval interval : intervals) {
            if (interval.end().isAfter(t) && (best == null || interval.end().isBefore(best))) {
                best = interval.end();
            }
        }
        return best;
    }

    void book(Dock dock, Interval slot) {
        dockBusy.get(dock.id()).add(slot);
        crewBusy.add(slot);
    }

    private static void addEndsAfter(List<Interval> intervals, Instant from, TreeSet<Instant> into) {
        for (Interval interval : intervals) {
            if (interval.end().isAfter(from)) {
                into.add(interval.end());
            }
        }
    }

    private static boolean overlapsAny(List<Interval> intervals, Interval slot) {
        for (Interval interval : intervals) {
            if (interval.overlaps(slot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The largest number of {@code intervals} running at the same instant inside {@code slot}.
     * A sweep over start/end events; an end at time t is processed before a start at t,
     * because intervals are half-open.
     */
    static int maxConcurrent(List<Interval> intervals, Interval slot) {
        List<long[]> events = new ArrayList<>();
        for (Interval interval : intervals) {
            if (interval.overlaps(slot)) {
                long start = Math.max(interval.start().toEpochMilli(), slot.start().toEpochMilli());
                long end = Math.min(interval.end().toEpochMilli(), slot.end().toEpochMilli());
                events.add(new long[] {start, +1});
                events.add(new long[] {end, -1});
            }
        }
        if (events.isEmpty()) {
            return 0;
        }
        events.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        int running = 0;
        int max = 0;
        for (long[] event : events) {
            running += (int) event[1];
            max = Math.max(max, running);
        }
        return max;
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
