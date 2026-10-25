package com.dockmind.scheduling.validation;

import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.model.Unscheduled;
import com.dockmind.scheduling.validation.Violation.Rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks any schedule against every rule, independently of how it was produced.
 *
 * <p>This is the safety net for the whole platform. Schedulers are tested against it, and from
 * Milestone 5 every recovery plan, including anything the AI assistant suggests, must pass it
 * before it can be approved. It deliberately shares no code with the schedulers, so a bug in
 * the scheduling logic can't hide itself here.
 */
public final class ScheduleValidator {

    private ScheduleValidator() {
    }

    public static List<Violation> validate(SchedulingProblem problem, Schedule schedule) {
        List<Violation> violations = new ArrayList<>();
        Map<String, Job> jobs = problem.jobsById();
        Map<String, Dock> docks = problem.docksById();

        Set<String> accounted = new HashSet<>();
        Map<String, List<Assignment>> byDock = new TreeMap<>();
        List<Assignment> valid = new ArrayList<>();

        for (Assignment a : schedule.assignments()) {
            Job job = jobs.get(a.jobId());
            Dock dock = docks.get(a.dockId());
            if (job == null) {
                violations.add(new Violation(a.jobId(), Rule.UNKNOWN_JOB, "not in the problem"));
                continue;
            }
            if (!accounted.add(a.jobId())) {
                violations.add(new Violation(a.jobId(), Rule.DUPLICATE_JOB, "assigned more than once"));
            }
            if (dock == null) {
                violations.add(new Violation(a.jobId(), Rule.UNKNOWN_DOCK, a.dockId()));
                continue;
            }
            checkAssignment(job, dock, a, violations);
            byDock.computeIfAbsent(dock.id(), k -> new ArrayList<>()).add(a);
            valid.add(a);
        }

        for (Unscheduled u : schedule.unscheduled()) {
            if (!jobs.containsKey(u.jobId())) {
                violations.add(new Violation(u.jobId(), Rule.UNKNOWN_JOB, "listed as unscheduled"));
            } else if (!accounted.add(u.jobId())) {
                violations.add(new Violation(u.jobId(), Rule.DUPLICATE_JOB, "both assigned and unscheduled"));
            }
        }
        jobs.keySet().stream().sorted()
                .filter(id -> !accounted.contains(id))
                .forEach(id -> violations.add(new Violation(id, Rule.MISSING_JOB, "neither assigned nor unscheduled")));

        byDock.values().forEach(list -> checkNoOverlap(list, violations));
        checkCrewCapacity(valid, problem.crewCapacity(), violations);
        return violations;
    }

    private static void checkAssignment(Job job, Dock dock, Assignment a, List<Violation> out) {
        if (!a.end().isAfter(a.start()) || !java.time.Duration.between(a.start(), a.end()).equals(job.loadingTime())) {
            out.add(new Violation(job.id(), Rule.WRONG_DURATION, "slot length differs from loading time"));
            return; // the remaining checks need a valid interval
        }
        Interval slot = a.slot();
        if (!dock.canServe(job)) {
            out.add(new Violation(job.id(), Rule.CAPABILITY_MISMATCH, "dock " + dock.id() + " lacks " + job.requiredCapabilities()));
        }
        if (a.start().isBefore(job.arrival())) {
            out.add(new Violation(job.id(), Rule.BEFORE_ARRIVAL, "starts before the truck arrives"));
        }
        if (job.latestStart() != null && a.start().isAfter(job.latestStart())) {
            out.add(new Violation(job.id(), Rule.AFTER_LATEST_START, "starts after its deadline"));
        }
        if (!dock.openHours().contains(slot)) {
            out.add(new Violation(job.id(), Rule.OUTSIDE_OPEN_HOURS, "outside dock " + dock.id() + " hours"));
        }
        for (Interval blocked : dock.blocked()) {
            if (blocked.overlaps(slot)) {
                out.add(new Violation(job.id(), Rule.DOCK_BLOCKED, "dock " + dock.id() + " is unavailable then"));
                break;
            }
        }
    }

    private static void checkNoOverlap(List<Assignment> onOneDock, List<Violation> out) {
        List<Assignment> sorted = onOneDock.stream().sorted(Assignment.ORDER).toList();
        for (int i = 1; i < sorted.size(); i++) {
            Assignment previous = sorted.get(i - 1);
            Assignment current = sorted.get(i);
            if (current.start().isBefore(previous.end())) {
                out.add(new Violation(current.jobId(), Rule.DOCK_OVERLAP,
                        "overlaps " + previous.jobId() + " on dock " + current.dockId()));
            }
        }
    }

    private static void checkCrewCapacity(List<Assignment> all, int capacity, List<Violation> out) {
        // Sweep: process ends before starts at the same instant (half-open intervals).
        record Event(long at, int delta, String jobId) { }
        List<Event> events = new ArrayList<>();
        for (Assignment a : all) {
            events.add(new Event(a.start().toEpochMilli(), +1, a.jobId()));
            events.add(new Event(a.end().toEpochMilli(), -1, a.jobId()));
        }
        events.sort(Comparator.comparingLong(Event::at).thenComparingInt(Event::delta).thenComparing(Event::jobId));
        int running = 0;
        Map<String, Boolean> reported = new HashMap<>();
        for (Event e : events) {
            running += e.delta();
            if (e.delta() > 0 && running > capacity && reported.putIfAbsent(e.jobId(), true) == null) {
                out.add(new Violation(e.jobId(), Rule.CREW_CAPACITY_EXCEEDED,
                        running + " loading at once, capacity is " + capacity));
            }
        }
    }
}
