package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.model.Unscheduled;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * DockMind's scheduler: an event-driven dispatcher, the way a good human dispatcher works.
 *
 * <p>It moves a clock forward through the day. At each moment it looks only at trucks that
 * have <b>already arrived</b> and, while a dock and a crew are free, starts the most urgent of
 * them: highest priority, then the closest deadline, then the longest waiting. It never
 * reserves future slots for trucks that haven't arrived, so idle capacity is never wasted.
 *
 * <p>Compared with FCFS, the only differences are which waiting truck goes next and which free
 * dock it takes (one without unneeded special equipment, when there is a choice). It uses the
 * same {@link Timeline} feasibility rules as {@link FcfsScheduler}.
 *
 * <p>A truck whose deadline passes while it waits, or that can no longer finish before the
 * docks close, is reported as unscheduled with the reason; it is never placed illegally.
 */
public final class DispatchScheduler implements Scheduler {

    public static final String NAME = "DOCKMIND_DISPATCH";

    /** Who goes next among waiting trucks. A total order, so results are deterministic. */
    static final Comparator<Job> URGENCY = Comparator
            .comparing(Job::priority, Comparator.reverseOrder())
            .thenComparing(Job::latestStart, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Job::arrival)
            .thenComparing(Job::id);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Schedule schedule(SchedulingProblem problem) {
        return schedule(problem, List.of());
    }

    @Override
    public Schedule schedule(SchedulingProblem problem, List<Assignment> fixed) {
        java.util.Set<String> fixedIds = FixedAppointments.idsOf(problem, fixed);
        Timeline timeline = new Timeline(problem, fixed);
        List<Dock> docks = problem.docks().stream()
                .sorted(Comparator.comparing(Dock::id)).toList();

        List<Assignment> assignments = new ArrayList<>(fixed);
        List<Unscheduled> unscheduled = new ArrayList<>();

        Deque<Job> notArrived = new ArrayDeque<>();
        for (Job job : problem.jobs().stream().filter(j -> !fixedIds.contains(j.id()))
                .sorted(Comparator.comparing(Job::arrival).thenComparing(Job::id)).toList()) {
            if (docks.stream().anyMatch(d -> d.canServe(job))) {
                notArrived.add(job);
            } else {
                unscheduled.add(new Unscheduled(job.id(), Unscheduled.Reason.NO_COMPATIBLE_DOCK));
            }
        }

        TreeSet<Job> waiting = new TreeSet<>(URGENCY);
        Instant now = notArrived.isEmpty() ? null : notArrived.peekFirst().arrival();

        while (now != null) {
            while (!notArrived.isEmpty() && !notArrived.peekFirst().arrival().isAfter(now)) {
                waiting.add(notArrived.pollFirst());
            }

            dropTrucksThatCanNoLongerBeServed(waiting, docks, timeline, now, unscheduled);

            // Start as many waiting trucks as possible right now, most urgent first.
            boolean started = true;
            while (started) {
                started = false;
                for (Job job : waiting) {
                    Optional<Dock> dock = freeDockNow(job, docks, timeline, now);
                    if (dock.isPresent()) {
                        Interval slot = Interval.of(now, job.loadingTime());
                        timeline.book(dock.get(), slot);
                        assignments.add(new Assignment(job.id(), dock.get().id(), slot.start(), slot.end()));
                        waiting.remove(job);
                        started = true;
                        break; // the set changed; restart from the most urgent truck
                    }
                }
            }

            now = nextEvent(now, notArrived, waiting, docks, timeline);
        }

        for (Job job : waiting) {
            unscheduled.add(new Unscheduled(job.id(), Unscheduled.Reason.NO_FEASIBLE_SLOT));
        }
        return new Schedule(name(), assignments, unscheduled);
    }

    /** A compatible dock where this truck can start at exactly {@code now}, preferring the least special one. */
    private static Optional<Dock> freeDockNow(Job job, List<Dock> docks, Timeline timeline, Instant now) {
        Dock best = null;
        for (Dock dock : docks) {
            Optional<Instant> start = timeline.earliestStart(dock, job);
            if (start.isPresent() && !start.get().isAfter(now)
                    && (best == null || dock.extraCapabilitiesFor(job) < best.extraCapabilitiesFor(job))) {
                best = dock;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Removes trucks that have missed their deadline or can't fit on any dock before closing. */
    private static void dropTrucksThatCanNoLongerBeServed(TreeSet<Job> waiting, List<Dock> docks, Timeline timeline,
                                                          Instant now, List<Unscheduled> unscheduled) {
        Iterator<Job> it = waiting.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            boolean deadlinePassed = job.latestStart() != null && job.latestStart().isBefore(now);
            boolean neverFits = docks.stream().noneMatch(d -> timeline.earliestStart(d, job).isPresent());
            if (deadlinePassed || neverFits) {
                it.remove();
                unscheduled.add(new Unscheduled(job.id(), Unscheduled.Reason.NO_FEASIBLE_SLOT));
            }
        }
    }

    /** The next moment anything changes: a truck arrives, or a dock, outage or crew is released. */
    private static Instant nextEvent(Instant now, Deque<Job> notArrived, TreeSet<Job> waiting,
                                     List<Dock> docks, Timeline timeline) {
        Instant next = notArrived.isEmpty() ? null : notArrived.peekFirst().arrival();
        if (!waiting.isEmpty()) {
            Optional<Instant> release = timeline.nextReleaseAfter(now);
            if (release.isPresent() && (next == null || release.get().isBefore(next))) {
                next = release.get();
            }
            // A waiting truck's own earliest start can also be the next event (e.g. after an outage).
            for (Job job : waiting) {
                for (Dock dock : docks) {
                    Optional<Instant> candidate = timeline.earliestStart(dock, job);
                    if (candidate.isPresent() && candidate.get().isAfter(now)
                            && (next == null || candidate.get().isBefore(next))) {
                        next = candidate.get();
                    }
                }
            }
        }
        return next;
    }
}
