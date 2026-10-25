package com.dockmind.scheduling.recovery;

import com.dockmind.scheduling.benchmark.WorkloadGenerator;
import com.dockmind.scheduling.engine.DispatchScheduler;
import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.validation.ScheduleValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.dockmind.scheduling.TestData.at;
import static com.dockmind.scheduling.TestData.dock;
import static com.dockmind.scheduling.TestData.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryPlannerTest {

    private final RecoveryPlanner planner = new RecoveryPlanner();

    /** Two docks; A and B are already loading at 08:00; C and D are booked for later. */
    private static final SchedulingProblem DAY = new SchedulingProblem(List.of(dock("D1"), dock("D2")),
            List.of(job("A", 0, 60), job("B", 0, 60), job("C", 60, 30), job("D", 90, 30, Priority.CRITICAL)), 2);

    private final Schedule current = new DispatchScheduler().schedule(DAY);

    @Test
    void lateTruckMovesOnlyItselfUnderMinimalChange() {
        RecoveryOutcome outcome = planner.plan(DAY, current, new Disruption.LateArrival("C", at(120)), at(30));

        RecoveryPlan minimal = plan(outcome, RecoveryPlanner.MINIMAL_CHANGE);
        assertThat(minimal.moved()).isEqualTo(1);
        assertThat(minimal.schedule().assignmentFor("C")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(120)));
        assertThat(ScheduleValidator.validate(outcome.adjustedProblem(), minimal.schedule())).isEmpty();
    }

    @Test
    void appointmentsAlreadyLoadingNeverMove() {
        RecoveryOutcome outcome = planner.plan(DAY, current, new Disruption.LateArrival("C", at(100)), at(30));

        for (RecoveryPlan plan : outcome.plans()) {
            assertThat(plan.schedule().assignmentFor("A")).isEqualTo(current.assignmentFor("A"));
            assertThat(plan.schedule().assignmentFor("B")).isEqualTo(current.assignmentFor("B"));
        }
    }

    @Test
    void nothingIsPlannedBeforeTheDecisionTime() {
        Instant decision = at(70);
        RecoveryOutcome outcome = planner.plan(DAY, current, new Disruption.DockOutage("D1", new Interval(at(70), at(200))), decision);

        for (RecoveryPlan plan : outcome.plans()) {
            plan.schedule().assignments().stream()
                    .filter(a -> !List.of("A", "B").contains(a.jobId()))
                    .forEach(a -> assertThat(a.start()).isAfterOrEqualTo(decision));
        }
    }

    @Test
    void outageMovesAppointmentsOffTheBrokenDock() {
        Interval outage = new Interval(at(60), at(240));
        RecoveryOutcome outcome = planner.plan(DAY, current, new Disruption.DockOutage("D1", outage), at(30));

        for (RecoveryPlan plan : outcome.plans()) {
            plan.schedule().assignments().stream()
                    .filter(a -> a.dockId().equals("D1"))
                    .forEach(a -> assertThat(a.slot().overlaps(outage)).isFalse());
            assertThat(ScheduleValidator.validate(outcome.adjustedProblem(), plan.schedule())).isEmpty();
        }
    }

    @Test
    void anOutageThatInterruptsLoadingReplacesThatTruck() {
        // A is loading on its dock 08:00–09:00; the dock fails at 08:30.
        Assignment a = current.assignmentFor("A").orElseThrow();
        Interval outage = new Interval(at(30), at(300));

        RecoveryOutcome outcome = planner.plan(DAY, current, new Disruption.DockOutage(a.dockId(), outage), at(30));

        for (RecoveryPlan plan : outcome.plans()) {
            plan.schedule().assignmentFor("A").ifPresent(moved -> assertThat(moved.slot().overlaps(outage) && moved.dockId().equals(a.dockId())).isFalse());
            assertThat(ScheduleValidator.validate(outcome.adjustedProblem(), plan.schedule())).isEmpty();
        }
    }

    @Test
    void recommendationProtectsUrgentTrucksFirst() {
        RecoveryOutcome outcome = planner.plan(DAY, current, new Disruption.DockOutage("D2", new Interval(at(60), at(400))), at(30));

        assertThat(outcome.recommended().schedule().assignmentFor("D")).isPresent();
        assertThat(outcome.plans()).isSortedAccordingTo(RecoveryPlanner.BEST_FIRST);
    }

    @Test
    void truckThatAlreadyStartedCannotBeReportedLate() {
        assertThatThrownBy(() -> planner.plan(DAY, current, new Disruption.LateArrival("A", at(90)), at(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownTruckOrDockIsRejected() {
        assertThatThrownBy(() -> planner.plan(DAY, current, new Disruption.LateArrival("NOPE", at(90)), at(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(DAY, current, new Disruption.DockOutage("NOPE", new Interval(at(0), at(10))), at(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameInputGivesTheSamePlans() {
        Disruption d = new Disruption.DockOutage("D1", new Interval(at(60), at(180)));

        assertThat(planner.plan(DAY, current, d, at(30))).isEqualTo(planner.plan(DAY, current, d, at(30)));
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8})
    void plansForGeneratedDaysAreAlwaysValid(long seed) {
        SchedulingProblem problem = WorkloadGenerator.generate(seed, 120);
        Schedule schedule = new DispatchScheduler().schedule(problem);
        Instant noon = WorkloadGenerator.DAY.plus(Duration.ofHours(12));

        RecoveryOutcome outcome = planner.plan(problem, schedule,
                new Disruption.DockOutage(problem.docks().get((int) (seed % problem.docks().size())).id(),
                        Interval.of(noon, Duration.ofHours(2))), noon);

        assertThat(outcome.plans()).isNotEmpty().allSatisfy(p ->
                assertThat(ScheduleValidator.validate(outcome.adjustedProblem(), p.schedule())).isEmpty());
    }

    private static RecoveryPlan plan(RecoveryOutcome outcome, String strategy) {
        return outcome.plans().stream().filter(p -> p.strategy().equals(strategy)).findFirst().orElseThrow();
    }
}
