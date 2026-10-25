package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.model.Unscheduled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dockmind.scheduling.TestData.at;
import static com.dockmind.scheduling.TestData.dock;
import static com.dockmind.scheduling.TestData.job;
import static com.dockmind.scheduling.TestData.jobWithDeadline;
import static org.assertj.core.api.Assertions.assertThat;

/** What makes the dispatcher different, each shown on a tiny scenario. */
class DispatchSchedulerTest {

    private final Scheduler fcfs = new FcfsScheduler();
    private final Scheduler dispatcher = new DispatchScheduler();

    @Test
    void criticalTruckTakesTheNextFreeDockAheadOfEarlierStandardTrucks() {
        // The dock is busy until 60. A standard truck waits from 10, a critical one from 20.
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("FIRST", 0, 60), job("STANDARD", 10, 30), job("CRITICAL", 20, 30, Priority.CRITICAL)), 1);

        Schedule byFcfs = fcfs.schedule(problem);
        Schedule byDispatcher = dispatcher.schedule(problem);

        assertThat(byFcfs.assignmentFor("CRITICAL")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(90)));
        assertThat(byDispatcher.assignmentFor("CRITICAL")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(60)));
        assertThat(byDispatcher.assignmentFor("STANDARD")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(90)));
    }

    @Test
    void neverHoldsAFreeDockForATruckThatHasNotArrived() {
        // A critical truck is due at 30, but the dock is free now: the standard truck starts now.
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("STANDARD", 0, 20), job("CRITICAL", 30, 30, Priority.CRITICAL)), 1);

        Schedule schedule = dispatcher.schedule(problem);

        assertThat(schedule.assignmentFor("STANDARD")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(0)));
        assertThat(schedule.assignmentFor("CRITICAL")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(30)));
    }

    @Test
    void amongWaitingTrucksTheClosestDeadlineGoesFirst() {
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("BLOCKER", 0, 30), jobWithDeadline("RELAXED", 5, 30, 200),
                        jobWithDeadline("TIGHT", 10, 30, 40)), 1);

        Schedule schedule = dispatcher.schedule(problem);

        assertThat(schedule.assignmentFor("TIGHT")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(30)));
        assertThat(schedule.assignmentFor("RELAXED")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(60)));
    }

    @Test
    void reportsATruckWhoseDeadlinePassesWhileWaiting() {
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("LONG", 0, 120), jobWithDeadline("TIGHT", 10, 30, 30)), 1);

        assertThat(dispatcher.schedule(problem).unscheduled())
                .containsExactly(new Unscheduled("TIGHT", Unscheduled.Reason.NO_FEASIBLE_SLOT));
    }

    @Test
    void prefersAPlainDockOverASpecialOneWhenBothAreFree() {
        var problem = new SchedulingProblem(List.of(dock("D1-REEFER", Capability.REFRIGERATED), dock("D2")),
                List.of(job("DRY", 0, 30)), 2);

        assertThat(dispatcher.schedule(problem).assignmentFor("DRY"))
                .hasValueSatisfying(a -> assertThat(a.dockId()).isEqualTo("D2"));
    }
}
