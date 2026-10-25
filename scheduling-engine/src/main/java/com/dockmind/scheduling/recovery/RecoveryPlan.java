package com.dockmind.scheduling.recovery;

import com.dockmind.scheduling.metrics.ScheduleMetrics;
import com.dockmind.scheduling.model.Schedule;

/**
 * One way to recover from a disruption.
 *
 * @param strategy  how it was produced ({@link RecoveryPlanner#MINIMAL_CHANGE} or {@link RecoveryPlanner#FULL_RESCHEDULE})
 * @param schedule  the complete proposed schedule, already checked by the validator
 * @param moved     appointments that differ from the current schedule (new dock or time, or dropped)
 * @param metrics   waiting time, unscheduled counts etc. for comparison
 */
public record RecoveryPlan(String strategy, Schedule schedule, int moved, ScheduleMetrics metrics) {
}
