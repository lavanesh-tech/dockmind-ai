package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;

/**
 * Turns a problem into a schedule. Implementations must be deterministic: the same problem
 * gives the same schedule every time, whatever order the jobs or docks are listed in.
 */
public interface Scheduler {

    String name();

    Schedule schedule(SchedulingProblem problem);

    /**
     * Schedules every job in {@code problem} except those in {@code fixed}, which are kept exactly
     * as given (they occupy their dock and a crew) and included in the result. Used for disruption
     * recovery, where trucks already loading, or appointments chosen to stay put, must not move.
     */
    Schedule schedule(SchedulingProblem problem, java.util.List<com.dockmind.scheduling.model.Assignment> fixed);
}
