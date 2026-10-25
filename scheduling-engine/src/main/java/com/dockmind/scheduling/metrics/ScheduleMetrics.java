package com.dockmind.scheduling.metrics;

import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.validation.ScheduleValidator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Measurable quality of a schedule.
 *
 * <p>Waiting time = loading start − truck arrival, for scheduled jobs only. Unscheduled jobs
 * are counted separately rather than hidden inside an average.
 *
 * @param jobs                  jobs in the problem
 * @param scheduled             jobs placed on a dock
 * @param unscheduled           jobs that could not be placed
 * @param avgWaitMinutes        mean wait over all scheduled jobs
 * @param p95WaitMinutes        95th percentile wait (nearest-rank method)
 * @param urgentAvgWaitMinutes  mean wait over scheduled EXPEDITED and CRITICAL jobs
 * @param urgentUnscheduled     EXPEDITED and CRITICAL jobs that could not be placed
 * @param dockUtilization       busy dock time ÷ available dock time (open hours minus outages)
 * @param violations            rule violations found by {@link ScheduleValidator}; must be 0
 */
public record ScheduleMetrics(String algorithm, int jobs, int scheduled, int unscheduled,
                              double avgWaitMinutes, double p95WaitMinutes,
                              double urgentAvgWaitMinutes, int urgentUnscheduled,
                              double dockUtilization, int violations) {

    public static ScheduleMetrics of(SchedulingProblem problem, Schedule schedule) {
        Map<String, Job> jobs = problem.jobsById();
        List<Double> waits = new ArrayList<>();
        List<Double> urgentWaits = new ArrayList<>();
        long busyMillis = 0;

        for (Assignment a : schedule.assignments()) {
            Job job = jobs.get(a.jobId());
            if (job == null) {
                continue;
            }
            double wait = minutes(a.waitFrom(job.arrival()));
            waits.add(wait);
            if (job.priority().isUrgent()) {
                urgentWaits.add(wait);
            }
            busyMillis += Duration.between(a.start(), a.end()).toMillis();
        }

        int urgentUnscheduled = (int) schedule.unscheduled().stream()
                .map(u -> jobs.get(u.jobId()))
                .filter(j -> j != null && j.priority().isUrgent())
                .count();

        return new ScheduleMetrics(
                schedule.algorithm(),
                problem.jobs().size(),
                schedule.assignments().size(),
                schedule.unscheduled().size(),
                mean(waits),
                percentile(waits, 95),
                mean(urgentWaits),
                urgentUnscheduled,
                availableMillis(problem) == 0 ? 0 : (double) busyMillis / availableMillis(problem),
                ScheduleValidator.validate(problem, schedule).size());
    }

    static double mean(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
    }

    /** Nearest-rank percentile: the smallest value with at least p% of values at or below it. */
    static double percentile(List<Double> values, int p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }

    private static long availableMillis(SchedulingProblem problem) {
        long total = 0;
        for (Dock dock : problem.docks()) {
            total += dock.openHours().duration().toMillis();
            for (Interval blocked : dock.blocked()) {
                Interval open = dock.openHours();
                if (blocked.overlaps(open)) {
                    long start = Math.max(blocked.start().toEpochMilli(), open.start().toEpochMilli());
                    long end = Math.min(blocked.end().toEpochMilli(), open.end().toEpochMilli());
                    total -= end - start;
                }
            }
        }
        return total;
    }

    private static double minutes(Duration d) {
        return d.toMillis() / 60_000.0;
    }
}
