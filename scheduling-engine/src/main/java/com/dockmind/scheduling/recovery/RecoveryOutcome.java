package com.dockmind.scheduling.recovery;

import com.dockmind.scheduling.model.SchedulingProblem;

import java.util.List;

/**
 * The result of planning a recovery.
 *
 * @param adjustedProblem the problem after applying the disruption and the decision time; every
 *                        plan is valid for exactly this problem
 * @param plans           all valid plans, best first; {@code plans.get(0)} is the recommendation
 */
public record RecoveryOutcome(SchedulingProblem adjustedProblem, List<RecoveryPlan> plans) {

    public RecoveryPlan recommended() {
        return plans.getFirst();
    }
}
