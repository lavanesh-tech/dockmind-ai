package com.dockmind.scheduling.benchmark;

import com.dockmind.scheduling.engine.DispatchScheduler;
import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.recovery.Disruption;
import com.dockmind.scheduling.recovery.RecoveryOutcome;
import com.dockmind.scheduling.recovery.RecoveryPlan;
import com.dockmind.scheduling.recovery.RecoveryPlanner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Seeded disruption scenarios, measuring how well recovery works.
 *
 * <p>For each seed: build a 140-truck day, schedule it, then at 12:00 inject disruptions one at a
 * time (each against the original schedule): 5 late trucks (delayed 30–90 min) and 5 dock outages
 * (2 hours). Both strategies are planned every time.
 *
 * <p><b>Recovery success</b> = the recommended plan is valid and leaves no urgent truck unscheduled
 * that was scheduled before the disruption.
 */
public final class RecoveryBenchmark {

    static final int LOAD = 140;
    static final Instant DECISION = WorkloadGenerator.DAY.plus(Duration.ofHours(12));

    private RecoveryBenchmark() {
    }

    public record Row(long seed, String kind, String recommended, int minimalMoved, int fullMoved,
                      int urgentLost, boolean success, double computeMillis) { }

    public static List<Row> run(long[] seeds) {
        RecoveryPlanner planner = new RecoveryPlanner();
        List<Row> rows = new ArrayList<>();
        for (long seed : seeds) {
            SchedulingProblem problem = WorkloadGenerator.generate(seed, LOAD);
            Schedule current = new DispatchScheduler().schedule(problem);
            Random random = new Random(seed * 7919);
            List<Assignment> later = current.assignments().stream().filter(a -> !a.start().isBefore(DECISION)).toList();
            int urgentUnscheduledBefore = com.dockmind.scheduling.metrics.ScheduleMetrics.of(problem, current).urgentUnscheduled();

            List<Disruption> disruptions = new ArrayList<>();
            for (int i = 0; i < 5 && !later.isEmpty(); i++) {
                Assignment victim = later.get(random.nextInt(later.size()));
                Instant newArrival = problem.jobsById().get(victim.jobId()).arrival().plus(Duration.ofMinutes(30 + random.nextInt(61)));
                disruptions.add(new Disruption.LateArrival(victim.jobId(), newArrival.isBefore(DECISION) ? DECISION : newArrival));
            }
            List<Dock> docks = problem.docks();
            for (int i = 0; i < 5; i++) {
                Dock dock = docks.get(random.nextInt(docks.size()));
                disruptions.add(new Disruption.DockOutage(dock.id(), Interval.of(DECISION, Duration.ofHours(2))));
            }

            for (Disruption d : disruptions) {
                long start = System.nanoTime();
                RecoveryOutcome outcome = planner.plan(problem, current, d, DECISION);
                double ms = (System.nanoTime() - start) / 1_000_000.0;
                RecoveryPlan best = outcome.recommended();
                int minimal = outcome.plans().stream().filter(p -> p.strategy().equals(RecoveryPlanner.MINIMAL_CHANGE))
                        .mapToInt(RecoveryPlan::moved).findFirst().orElse(-1);
                int full = outcome.plans().stream().filter(p -> p.strategy().equals(RecoveryPlanner.FULL_RESCHEDULE))
                        .mapToInt(RecoveryPlan::moved).findFirst().orElse(-1);
                int urgentLost = Math.max(0, best.metrics().urgentUnscheduled() - urgentUnscheduledBefore);
                rows.add(new Row(seed, d instanceof Disruption.LateArrival ? "LATE_ARRIVAL" : "DOCK_OUTAGE",
                        best.strategy(), minimal, full, urgentLost, best.metrics().violations() == 0 && urgentLost == 0, ms));
            }
        }
        return rows;
    }

    static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder("seed,kind,recommended,minimal_moved,full_moved,urgent_lost,success,compute_ms\n");
        for (Row r : rows) {
            sb.append(String.format(Locale.ROOT, "%d,%s,%s,%d,%d,%d,%b,%.3f%n", r.seed(), r.kind(), r.recommended(),
                    r.minimalMoved(), r.fullMoved(), r.urgentLost(), r.success(), r.computeMillis()));
        }
        return sb.toString();
    }

    static String toMarkdown(List<Row> rows) {
        StringBuilder sb = new StringBuilder("\n## Disruption recovery\n\n");
        sb.append("140 trucks/day, decision time 12:00, seeds ").append(java.util.Arrays.toString(BenchmarkRunner.SEEDS))
                .append(". Each disruption is planned against the original schedule.\n\n");
        sb.append("| Disruption | Cases | Recovery success | Recommended MINIMAL_CHANGE | Avg moved (minimal) | Avg moved (full) | Median compute (ms) |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (String kind : List.of("LATE_ARRIVAL", "DOCK_OUTAGE")) {
            List<Row> g = rows.stream().filter(r -> r.kind().equals(kind)).toList();
            if (g.isEmpty()) {
                continue;
            }
            long ok = g.stream().filter(Row::success).count();
            long minimalChosen = g.stream().filter(r -> r.recommended().equals(RecoveryPlanner.MINIMAL_CHANGE)).count();
            double[] ms = g.stream().mapToDouble(Row::computeMillis).sorted().toArray();
            sb.append(String.format(Locale.ROOT, "| %s | %d | %.0f%% | %d of %d | %.1f | %.1f | %.1f |%n", kind, g.size(),
                    100.0 * ok / g.size(), minimalChosen, g.size(),
                    g.stream().mapToInt(Row::minimalMoved).average().orElse(0),
                    g.stream().mapToInt(Row::fullMoved).average().orElse(0),
                    ms[ms.length / 2]));
        }
        sb.append("\n*Recovery success* = the recommended plan is valid and no urgent truck that was scheduled before ")
                .append("the disruption ends up unscheduled. *Moved* = appointments whose dock or start time changed, or that were dropped.\n");
        return sb.toString();
    }
}
