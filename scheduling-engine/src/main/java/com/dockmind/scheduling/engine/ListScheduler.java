package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.model.Unscheduled;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Greedy "list scheduling": sort the jobs, then place each one, in order, on the dock and
 * time chosen by a scoring rule. Both DockMind schedulers share this loop and the same
 * {@link Timeline} feasibility rules. They differ only in the two decisions an operator
 * actually makes:
 * <ol>
 *   <li>{@link #jobOrder()}: who goes first;</li>
 *   <li>{@link #score}: which dock to use when several are possible.</li>
 * </ol>
 * This makes the benchmark a fair comparison: any difference in results comes from those
 * decisions, not from one scheduler having better feasibility code.
 */
abstract class ListScheduler implements Scheduler {

    /** Must be a total order (end with the job ID) so the schedule is deterministic. */
    protected abstract Comparator<Job> jobOrder();

    /** Lower is better. Ties are broken by dock ID. */
    protected abstract long score(Job job, Dock dock, Instant start);

    @Override
    public final Schedule schedule(SchedulingProblem problem) {
        return schedule(problem, List.of());
    }

    @Override
    public final Schedule schedule(SchedulingProblem problem, List<Assignment> fixed) {
        java.util.Set<String> fixedIds = FixedAppointments.idsOf(problem, fixed);
        Timeline timeline = new Timeline(problem, fixed);
        List<Dock> docks = problem.docks().stream().sorted(Comparator.comparing(Dock::id)).toList();
        List<Job> jobs = problem.jobs().stream().filter(j -> !fixedIds.contains(j.id())).sorted(jobOrder()).toList();

        List<Assignment> assignments = new ArrayList<>(fixed);
        List<Unscheduled> unscheduled = new ArrayList<>();

        for (Job job : jobs) {
            List<Dock> compatible = docks.stream().filter(d -> d.canServe(job)).toList();
            if (compatible.isEmpty()) {
                unscheduled.add(new Unscheduled(job.id(), Unscheduled.Reason.NO_COMPATIBLE_DOCK));
                continue;
            }

            Dock bestDock = null;
            Instant bestStart = null;
            long bestScore = Long.MAX_VALUE;
            for (Dock dock : compatible) { // already sorted by id, so '<' keeps the first on ties
                Optional<Instant> start = timeline.earliestStart(dock, job);
                if (start.isPresent()) {
                    long s = score(job, dock, start.get());
                    if (s < bestScore) {
                        bestScore = s;
                        bestDock = dock;
                        bestStart = start.get();
                    }
                }
            }

            if (bestDock == null) {
                unscheduled.add(new Unscheduled(job.id(), Unscheduled.Reason.NO_FEASIBLE_SLOT));
                continue;
            }
            Interval slot = Interval.of(bestStart, job.loadingTime());
            timeline.book(bestDock, slot);
            assignments.add(new Assignment(job.id(), bestDock.id(), slot.start(), slot.end()));
        }
        return new Schedule(name(), assignments, unscheduled);
    }
}
