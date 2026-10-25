package com.dockmind.scheduling.recovery;

import com.dockmind.scheduling.engine.DispatchScheduler;
import com.dockmind.scheduling.engine.Scheduler;
import com.dockmind.scheduling.metrics.ScheduleMetrics;
import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.validation.ScheduleValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns "this truck is late" or "this dock is down" into concrete, validated alternatives.
 *
 * <p>Rules every plan obeys:
 * <ul>
 *   <li><b>The past is fixed.</b> Appointments that started before the decision time stay as they
 *       are, unless the outage interrupts them, in which case that truck is re-placed.</li>
 *   <li><b>Nothing starts in the past.</b> Remaining trucks become available no earlier than the
 *       decision time. A truck whose deadline has already passed keeps its place in the queue but
 *       loses the deadline: it should still be served as soon as possible.</li>
 *   <li><b>Every plan passes the independent validator</b> against the adjusted problem. Plans that
 *       don't are discarded.</li>
 * </ul>
 *
 * <p>Strategies:
 * <ul>
 *   <li>{@link #MINIMAL_CHANGE}: keep every unaffected appointment where it is; re-place only the
 *       affected trucks (and any previously unscheduled ones). Least disruptive for drivers and staff.</li>
 *   <li>{@link #FULL_RESCHEDULE}: re-plan everything that hasn't started. Can find a better overall
 *       schedule, at the cost of moving more appointments.</li>
 * </ul>
 *
 * <p>Recommendation order (deterministic): fewest urgent trucks left unscheduled, then fewest
 * unscheduled, then fewest moved appointments, then lowest average wait.
 */
public final class RecoveryPlanner {

    public static final String MINIMAL_CHANGE = "MINIMAL_CHANGE";
    public static final String FULL_RESCHEDULE = "FULL_RESCHEDULE";

    static final Comparator<RecoveryPlan> BEST_FIRST = Comparator
            .comparingInt((RecoveryPlan p) -> p.metrics().urgentUnscheduled())
            .thenComparingInt(p -> p.metrics().unscheduled())
            .thenComparingInt(RecoveryPlan::moved)
            .thenComparingDouble(p -> p.metrics().avgWaitMinutes())
            .thenComparing(RecoveryPlan::strategy);

    private final Scheduler scheduler = new DispatchScheduler();

    public RecoveryOutcome plan(SchedulingProblem problem, Schedule current, Disruption disruption, Instant decisionTime) {
        Objects.requireNonNull(decisionTime, "decisionTime");
        SchedulingProblem disrupted = apply(problem, disruption);
        Map<String, Dock> docks = disrupted.docksById();

        // Appointments already under way at the decision time are kept, unless the outage interrupts them.
        List<Assignment> started = current.assignments().stream()
                .filter(a -> a.start().isBefore(decisionTime))
                .filter(a -> fitsDock(a, docks.get(a.dockId())))
                .toList();
        Set<String> startedIds = started.stream().map(Assignment::jobId).collect(Collectors.toSet());
        if (disruption instanceof Disruption.LateArrival late && startedIds.contains(late.jobId())) {
            throw new IllegalArgumentException("Truck " + late.jobId() + " has already started loading");
        }

        SchedulingProblem adjusted = notBefore(disrupted, decisionTime, startedIds);
        Map<String, Job> jobs = adjusted.jobsById();

        // MINIMAL_CHANGE also keeps every later appointment that is still valid on its own.
        List<Assignment> stillValid = new ArrayList<>(started);
        for (Assignment a : current.assignments()) {
            if (startedIds.contains(a.jobId()) || !jobs.containsKey(a.jobId())) {
                continue;
            }
            boolean affected = disruption instanceof Disruption.LateArrival late && late.jobId().equals(a.jobId());
            if (!affected && fitsDock(a, docks.get(a.dockId())) && fitsJob(a, jobs.get(a.jobId()))) {
                stillValid.add(a);
            }
        }

        List<RecoveryPlan> plans = new ArrayList<>();
        addIfValid(plans, MINIMAL_CHANGE, adjusted, current, scheduler.schedule(adjusted, stillValid));
        addIfValid(plans, FULL_RESCHEDULE, adjusted, current, scheduler.schedule(adjusted, started));
        plans.sort(BEST_FIRST);
        if (plans.isEmpty()) {
            throw new IllegalStateException("No valid recovery plan could be produced");
        }
        return new RecoveryOutcome(adjusted, List.copyOf(plans));
    }

    /** How many jobs have a different (dock, start) than before, including ones that lost their slot. */
    public static int moved(Schedule before, Schedule after) {
        Map<String, Assignment> old = before.assignments().stream()
                .collect(Collectors.toMap(Assignment::jobId, a -> a));
        Map<String, Assignment> now = after.assignments().stream()
                .collect(Collectors.toMap(Assignment::jobId, a -> a));
        int moved = 0;
        for (Map.Entry<String, Assignment> e : old.entrySet()) {
            Assignment n = now.get(e.getKey());
            if (n == null || !n.dockId().equals(e.getValue().dockId()) || !n.start().equals(e.getValue().start())) {
                moved++;
            }
        }
        return moved;
    }

    private static void addIfValid(List<RecoveryPlan> plans, String strategy, SchedulingProblem problem,
                                   Schedule current, Schedule proposed) {
        Schedule named = new Schedule(strategy, proposed.assignments(), proposed.unscheduled());
        if (ScheduleValidator.validate(problem, named).isEmpty()) {
            plans.add(new RecoveryPlan(strategy, named, moved(current, named), ScheduleMetrics.of(problem, named)));
        }
    }

    static SchedulingProblem apply(SchedulingProblem problem, Disruption disruption) {
        return switch (disruption) {
            case Disruption.LateArrival late -> {
                if (!problem.jobsById().containsKey(late.jobId())) {
                    throw new IllegalArgumentException("Unknown truck " + late.jobId());
                }
                yield new SchedulingProblem(problem.docks(),
                        problem.jobs().stream().map(j -> j.id().equals(late.jobId()) ? withArrival(j, late.newArrival()) : j).toList(),
                        problem.crewCapacity());
            }
            case Disruption.DockOutage outage -> {
                if (!problem.docksById().containsKey(outage.dockId())) {
                    throw new IllegalArgumentException("Unknown dock " + outage.dockId());
                }
                yield new SchedulingProblem(
                        problem.docks().stream().map(d -> d.id().equals(outage.dockId()) ? d.withBlocked(outage.period()) : d).toList(),
                        problem.jobs(), problem.crewCapacity());
            }
        };
    }

    /** Remaining trucks can't start before the decision time; started ones keep their data. */
    static SchedulingProblem notBefore(SchedulingProblem problem, Instant decisionTime, Set<String> startedIds) {
        List<Job> jobs = problem.jobs().stream()
                .map(j -> startedIds.contains(j.id()) || !j.arrival().isBefore(decisionTime) ? j : withArrival(j, decisionTime))
                .toList();
        return new SchedulingProblem(problem.docks(), jobs, problem.crewCapacity());
    }

    /** Moves a job's availability, dropping a deadline that can no longer be met. */
    static Job withArrival(Job job, Instant arrival) {
        Instant latest = job.latestStart() != null && !job.latestStart().isBefore(arrival) ? job.latestStart() : null;
        return new Job(job.id(), job.requiredCapabilities(), job.priority(), arrival, job.loadingTime(), latest);
    }

    private static boolean fitsDock(Assignment a, Dock dock) {
        if (dock == null || !dock.openHours().contains(a.slot())) {
            return false;
        }
        Interval slot = a.slot();
        return dock.blocked().stream().noneMatch(b -> b.overlaps(slot));
    }

    private static boolean fitsJob(Assignment a, Job job) {
        return !a.start().isBefore(job.arrival())
                && (job.latestStart() == null || !a.start().isAfter(job.latestStart()));
    }
}
