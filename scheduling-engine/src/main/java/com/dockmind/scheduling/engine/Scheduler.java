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
}
