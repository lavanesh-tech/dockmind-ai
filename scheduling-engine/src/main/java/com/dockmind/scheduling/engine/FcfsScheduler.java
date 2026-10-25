package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Job;

import java.time.Instant;
import java.util.Comparator;

/**
 * The baseline: first come, first served.
 *
 * <p>Trucks are handled strictly in arrival order. Each takes the compatible dock where it can
 * start soonest. This is a reasonable baseline, not a straw man: it already uses every dock
 * and fills gaps. It just ignores priority and deadlines, and doesn't think about which docks
 * are scarce.
 */
public final class FcfsScheduler extends ListScheduler {

    public static final String NAME = "FCFS";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected Comparator<Job> jobOrder() {
        return Comparator.comparing(Job::arrival).thenComparing(Job::id);
    }

    @Override
    protected long score(Job job, Dock dock, Instant start) {
        return start.getEpochSecond();
    }
}
