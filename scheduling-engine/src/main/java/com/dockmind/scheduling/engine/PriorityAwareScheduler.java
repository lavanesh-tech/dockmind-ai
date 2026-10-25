package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Job;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

/**
 * A "reserve-ahead" design iteration, kept in the benchmark for comparison. It uses the same
 * feasibility rules as {@link FcfsScheduler}, but makes two different decisions:
 *
 * <ol>
 *   <li><b>Order:</b> most urgent first, then the tightest deadline, then arrival order.
 *       Critical trucks are placed before standard ones can take their slots, and among
 *       equals, the job closest to missing its deadline goes first.</li>
 *   <li><b>Dock choice:</b> the earliest start, plus a small penalty for each special
 *       capability the job doesn't need. A dry-goods truck will wait up to
 *       {@link #SPECIAL_DOCK_PENALTY} minutes per capability rather than take the
 *       refrigerated dock, keeping scarce docks free for trucks that really need them.</li>
 * </ol>
 *
 * <p>Measured result: urgent trucks almost never wait, but because urgent trucks are booked
 * across the whole day before anyone else, the timeline fragments and standard trucks wait
 * noticeably longer. {@link DispatchScheduler} fixes this by deciding only among trucks that
 * have arrived, and is DockMind's production scheduler. See docs/BENCHMARKS.md.
 */
public final class PriorityAwareScheduler extends ListScheduler {

    public static final String NAME = "PRIORITY_AWARE";

    /** Minutes of extra waiting accepted to avoid occupying one unneeded special capability. */
    public static final Duration SPECIAL_DOCK_PENALTY = Duration.ofMinutes(20);

    private static final Comparator<Job> ORDER = Comparator
            .comparing(Job::priority, Comparator.reverseOrder())
            .thenComparing(Job::latestStart, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Job::arrival)
            .thenComparing(Job::id);

    private final long penaltySeconds;

    public PriorityAwareScheduler() {
        this(SPECIAL_DOCK_PENALTY);
    }

    /** Lets the benchmark and tests try other penalty values. */
    public PriorityAwareScheduler(Duration specialDockPenalty) {
        this.penaltySeconds = specialDockPenalty.toSeconds();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected Comparator<Job> jobOrder() {
        return ORDER;
    }

    @Override
    protected long score(Job job, Dock dock, Instant start) {
        return start.getEpochSecond() + dock.extraCapabilitiesFor(job) * penaltySeconds;
    }
}
