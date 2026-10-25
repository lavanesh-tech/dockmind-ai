package com.dockmind.scheduling.benchmark;

import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.SchedulingProblem;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds synthetic but realistic warehouse days from a seed. The same seed always gives the
 * same day, on any machine, because {@link Random} with a fixed seed is specified to produce
 * the same sequence. That is what makes the benchmark reproducible.
 *
 * <p>The model (all numbers are assumptions, documented in docs/BENCHMARKS.md):
 * <ul>
 *   <li>8 docks open 06:00–22:00: 2 refrigerated, 1 hazmat, 1 side-load + oversized, 4 general.</li>
 *   <li>6 loading crews, so at most 6 docks can be loading at once.</li>
 *   <li>Arrivals cluster around a morning peak (09:00) and an afternoon peak (14:00).</li>
 *   <li>20% need refrigeration, 5% hazmat, 5% side-load, 3% oversized.</li>
 *   <li>80% standard, 15% expedited, 5% critical. Deadlines are arrival + 4 h, 90 min and 30 min.</li>
 *   <li>Loading time is 10 min setup + 0.75 min per pallet, for 1–60 pallets (about 33 min on average).</li>
 * </ul>
 */
public final class WorkloadGenerator {

    public static final Instant DAY = Instant.parse("2026-11-02T00:00:00Z");
    static final Interval OPEN = new Interval(DAY.plus(Duration.ofHours(6)), DAY.plus(Duration.ofHours(22)));
    static final int CREWS = 6;

    private WorkloadGenerator() {
    }

    public static List<Dock> standardDocks() {
        return List.of(
                Dock.open("D01-REEFER", EnumSet.of(Capability.REFRIGERATED), OPEN),
                Dock.open("D02-REEFER", EnumSet.of(Capability.REFRIGERATED), OPEN),
                Dock.open("D03-HAZMAT", EnumSet.of(Capability.HAZMAT), OPEN),
                Dock.open("D04-SIDE", EnumSet.of(Capability.SIDE_LOAD, Capability.OVERSIZED), OPEN),
                Dock.open("D05", Set.of(), OPEN),
                Dock.open("D06", Set.of(), OPEN),
                Dock.open("D07", Set.of(), OPEN),
                Dock.open("D08", Set.of(), OPEN));
    }

    public static SchedulingProblem generate(long seed, int jobCount) {
        Random random = new Random(seed);
        List<Job> jobs = new ArrayList<>(jobCount);
        for (int i = 0; i < jobCount; i++) {
            jobs.add(randomJob(random, i));
        }
        return new SchedulingProblem(standardDocks(), jobs, CREWS);
    }

    private static Job randomJob(Random random, int index) {
        Instant arrival = randomArrival(random);

        Set<Capability> required = EnumSet.noneOf(Capability.class);
        double kind = random.nextDouble();
        if (kind < 0.20) {
            required.add(Capability.REFRIGERATED);
        } else if (kind < 0.25) {
            required.add(Capability.HAZMAT);
        } else if (kind < 0.30) {
            required.add(Capability.SIDE_LOAD);
        } else if (kind < 0.33) {
            required.add(Capability.OVERSIZED);
        }

        double p = random.nextDouble();
        Priority priority = p < 0.80 ? Priority.STANDARD : p < 0.95 ? Priority.EXPEDITED : Priority.CRITICAL;
        Duration slack = switch (priority) {
            case STANDARD -> Duration.ofHours(4);
            case EXPEDITED -> Duration.ofMinutes(90);
            case CRITICAL -> Duration.ofMinutes(30);
        };

        int pallets = 1 + random.nextInt(60);
        Duration loading = Duration.ofSeconds(10 * 60 + pallets * 45L);

        return new Job("J%04d".formatted(index), required, priority, arrival, loading, arrival.plus(slack));
    }

    /** Two daily peaks; clamped so every truck arrives while docks are open and can still finish. */
    private static Instant randomArrival(Random random) {
        double peakHour = random.nextDouble() < 0.55 ? 9.0 : 14.0;
        double hour = peakHour + random.nextGaussian() * 2.0;
        hour = Math.max(6.0, Math.min(19.5, hour));
        long minutes = Math.round(hour * 60);
        return DAY.plus(Duration.ofMinutes(minutes));
    }
}
