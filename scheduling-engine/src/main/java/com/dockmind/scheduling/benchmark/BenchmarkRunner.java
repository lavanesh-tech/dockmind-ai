package com.dockmind.scheduling.benchmark;

import com.dockmind.scheduling.engine.DispatchScheduler;
import com.dockmind.scheduling.engine.FcfsScheduler;
import com.dockmind.scheduling.engine.PriorityAwareScheduler;
import com.dockmind.scheduling.engine.Scheduler;
import com.dockmind.scheduling.metrics.ScheduleMetrics;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * Runs every scheduler on identical seeded workloads and writes the results.
 *
 * <p>Outputs, in the directory given as the first argument:
 * <ul>
 *   <li>{@code results.csv}: one row per (load, seed, algorithm)</li>
 *   <li>{@code summary.json}: means per (load, algorithm)</li>
 *   <li>{@code RESULTS.md}: the same summary as a readable table</li>
 * </ul>
 * It fails (non-zero exit) if any schedule breaks a rule, so invalid results are never published.
 */
public final class BenchmarkRunner {

    /**
     * Daily truck counts. With 6 crews × 16 h and ~33 min average loading, the day's capacity is
     * roughly 175 trucks, so these are about 55%, 80% and 100% of capacity: light, busy, saturated.
     */
    static final int[] LOADS = {100, 140, 180};
    static final long[] SEEDS = {1, 2, 3, 4, 5};
    /** Baseline first, then the reserve-ahead design iteration, then DockMind's dispatcher. */
    static final List<String> ALGORITHMS = List.of(
            FcfsScheduler.NAME, PriorityAwareScheduler.NAME, DispatchScheduler.NAME);
    private static final int WARMUP_RUNS = 2;
    private static final int TIMED_RUNS = 5;

    private BenchmarkRunner() {
    }

    public record Row(int load, long seed, ScheduleMetrics metrics, double computeMillis) { }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/benchmarks");
        List<Row> rows = run(LOADS, SEEDS);

        long invalid = rows.stream().filter(r -> r.metrics().violations() > 0).count();
        if (invalid > 0) {
            throw new IllegalStateException(invalid + " benchmark schedules broke scheduling rules; results not written");
        }

        Files.createDirectories(out);
        Files.writeString(out.resolve("results.csv"), toCsv(rows));
        Files.writeString(out.resolve("summary.json"), toJson(rows));
        Files.writeString(out.resolve("RESULTS.md"), toMarkdown(rows));
        System.out.println("Benchmark written to " + out.toAbsolutePath().normalize());
        System.out.print(toMarkdown(rows));
    }

    public static List<Row> run(int[] loads, long[] seeds) {
        List<Scheduler> schedulers = List.of(new FcfsScheduler(), new PriorityAwareScheduler(), new DispatchScheduler());
        List<Row> rows = new ArrayList<>();
        for (int load : loads) {
            for (long seed : seeds) {
                SchedulingProblem problem = WorkloadGenerator.generate(seed, load);
                for (Scheduler scheduler : schedulers) {
                    Schedule schedule = scheduler.schedule(problem);
                    rows.add(new Row(load, seed, ScheduleMetrics.of(problem, schedule), medianMillis(scheduler, problem)));
                }
            }
        }
        return rows;
    }

    /** Median of several timed runs after warm-up, so JIT compilation doesn't distort the number. */
    private static double medianMillis(Scheduler scheduler, SchedulingProblem problem) {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            scheduler.schedule(problem);
        }
        double[] times = new double[TIMED_RUNS];
        for (int i = 0; i < TIMED_RUNS; i++) {
            long start = System.nanoTime();
            scheduler.schedule(problem);
            times[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(times);
        return times[TIMED_RUNS / 2];
    }

    static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder(
                "load,seed,algorithm,jobs,scheduled,unscheduled,avg_wait_min,p95_wait_min,"
                        + "urgent_avg_wait_min,urgent_unscheduled,dock_utilization,violations,compute_ms\n");
        for (Row r : rows) {
            ScheduleMetrics m = r.metrics();
            sb.append(String.format(Locale.ROOT, "%d,%d,%s,%d,%d,%d,%.2f,%.2f,%.2f,%d,%.4f,%d,%.3f%n",
                    r.load(), r.seed(), m.algorithm(), m.jobs(), m.scheduled(), m.unscheduled(),
                    m.avgWaitMinutes(), m.p95WaitMinutes(), m.urgentAvgWaitMinutes(), m.urgentUnscheduled(),
                    m.dockUtilization(), m.violations(), r.computeMillis()));
        }
        return sb.toString();
    }

    static String toJson(List<Row> rows) {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"seeds\": ").append(Arrays.toString(SEEDS)).append(",\n");
        sb.append("  \"javaVersion\": \"").append(System.getProperty("java.version")).append("\",\n");
        sb.append("  \"summary\": [\n");
        List<String> entries = new ArrayList<>();
        for (int load : distinctLoads(rows)) {
            for (String algorithm : ALGORITHMS) {
                List<Row> group = rows.stream()
                        .filter(r -> r.load() == load && r.metrics().algorithm().equals(algorithm)).toList();
                entries.add(String.format(Locale.ROOT,
                        "    {\"load\": %d, \"algorithm\": \"%s\", \"runs\": %d, \"avgWaitMin\": %.2f, "
                                + "\"p95WaitMin\": %.2f, \"urgentAvgWaitMin\": %.2f, \"unscheduled\": %.2f, "
                                + "\"urgentUnscheduled\": %.2f, \"dockUtilization\": %.4f, \"computeMs\": %.3f}",
                        load, algorithm, group.size(),
                        avg(group, r -> r.metrics().avgWaitMinutes()),
                        avg(group, r -> r.metrics().p95WaitMinutes()),
                        avg(group, r -> r.metrics().urgentAvgWaitMinutes()),
                        avg(group, r -> r.metrics().unscheduled()),
                        avg(group, r -> r.metrics().urgentUnscheduled()),
                        avg(group, r -> r.metrics().dockUtilization()),
                        avg(group, Row::computeMillis)));
            }
        }
        sb.append(String.join(",\n", entries)).append("\n  ]\n}\n");
        return sb.toString();
    }

    static String toMarkdown(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Benchmark results\n\n");
        sb.append("_Generated by `BenchmarkRunner` during `./mvnw verify`. Do not edit by hand._\n\n");
        sb.append("Synthetic workloads only (see [BENCHMARKS.md](../BENCHMARKS.md) for the model and its assumptions). ");
        sb.append("Each cell is the mean over seeds ").append(Arrays.toString(SEEDS))
                .append(". Java ").append(System.getProperty("java.version")).append(".\n\n");
        sb.append("| Trucks/day | Algorithm | Avg wait (min) | p95 wait (min) | Urgent avg wait (min) "
                + "| Unscheduled | Urgent unscheduled | Dock utilization | Compute (ms) |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (int load : distinctLoads(rows)) {
            for (String algorithm : ALGORITHMS) {
                List<Row> g = rows.stream()
                        .filter(r -> r.load() == load && r.metrics().algorithm().equals(algorithm)).toList();
                sb.append(String.format(Locale.ROOT, "| %d | %s | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f%% | %.1f |%n",
                        load, algorithm,
                        avg(g, r -> r.metrics().avgWaitMinutes()),
                        avg(g, r -> r.metrics().p95WaitMinutes()),
                        avg(g, r -> r.metrics().urgentAvgWaitMinutes()),
                        avg(g, r -> r.metrics().unscheduled()),
                        avg(g, r -> r.metrics().urgentUnscheduled()),
                        100 * avg(g, r -> r.metrics().dockUtilization()),
                        avg(g, Row::computeMillis)));
            }
        }
        sb.append("\nAll ").append(rows.size()).append(" schedules passed the independent validator with 0 violations.\n");
        return sb.toString();
    }

    private static int[] distinctLoads(List<Row> rows) {
        return rows.stream().mapToInt(Row::load).distinct().sorted().toArray();
    }

    private static double avg(List<Row> rows, ToDoubleFunction<Row> f) {
        return rows.stream().mapToDouble(f).average().orElse(0);
    }
}
