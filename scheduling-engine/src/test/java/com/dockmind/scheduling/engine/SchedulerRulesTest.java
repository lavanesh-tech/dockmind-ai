package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.model.Unscheduled;
import com.dockmind.scheduling.validation.ScheduleValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.dockmind.scheduling.TestData.ALL_DAY;
import static com.dockmind.scheduling.TestData.at;
import static com.dockmind.scheduling.TestData.dock;
import static com.dockmind.scheduling.TestData.job;
import static com.dockmind.scheduling.TestData.jobWithDeadline;
import static org.assertj.core.api.Assertions.assertThat;

/** Rules every scheduler must obey. Each test runs against every scheduler. */
class SchedulerRulesTest {

    static Stream<Arguments> schedulers() {
        return Stream.of(Arguments.of(new FcfsScheduler()), Arguments.of(new PriorityAwareScheduler()),
                Arguments.of(new DispatchScheduler()));
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void loadingNeverStartsBeforeTheTruckArrives(Scheduler scheduler) {
        var problem = new SchedulingProblem(List.of(dock("D1")), List.of(job("J1", 45, 30)), 1);

        Schedule schedule = scheduler.schedule(problem);

        assertThat(schedule.assignmentFor("J1")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(45)));
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void secondTruckWaitsWhenTheOnlyDockIsBusy(Scheduler scheduler) {
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("J1", 0, 60), job("J2", 10, 30)), 2);

        Schedule schedule = scheduler.schedule(problem);

        assertThat(schedule.assignmentFor("J2")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(60)));
        assertThat(ScheduleValidator.validate(problem, schedule)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void jobGoesOnlyToADockWithItsCapabilities(Scheduler scheduler) {
        var problem = new SchedulingProblem(
                List.of(dock("D1"), dock("D2", Capability.REFRIGERATED)),
                List.of(job("COLD", 0, 30, Priority.STANDARD, Capability.REFRIGERATED)), 2);

        assertThat(scheduler.schedule(problem).assignmentFor("COLD"))
                .hasValueSatisfying(a -> assertThat(a.dockId()).isEqualTo("D2"));
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void reportsWhenNoDockHasTheCapability(Scheduler scheduler) {
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("HAZ", 0, 30, Priority.STANDARD, Capability.HAZMAT)), 1);

        assertThat(scheduler.schedule(problem).unscheduled())
                .containsExactly(new Unscheduled("HAZ", Unscheduled.Reason.NO_COMPATIBLE_DOCK));
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void reportsWhenTheDeadlineCannotBeMet(Scheduler scheduler) {
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("J1", 0, 120), jobWithDeadline("J2", 10, 30, 60)), 1);

        Schedule schedule = scheduler.schedule(problem);

        // FCFS places J1 first; the priority-aware scheduler places J2 first (tighter deadline).
        // Either way, every job is either placed legally or reported, never placed illegally.
        assertThat(ScheduleValidator.validate(problem, schedule)).isEmpty();
        assertThat(schedule.assignments().size() + schedule.unscheduled().size()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void avoidsDockOutages(Scheduler scheduler) {
        Dock withOutage = new Dock("D1", Set.of(), ALL_DAY, List.of(new Interval(at(0), at(90))));
        var problem = new SchedulingProblem(List.of(withOutage), List.of(job("J1", 0, 30)), 1);

        assertThat(scheduler.schedule(problem).assignmentFor("J1"))
                .hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(90)));
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void respectsCrewCapacityEvenWhenDocksAreFree(Scheduler scheduler) {
        // Three free docks but only two crews: the third truck must wait for a crew.
        var problem = new SchedulingProblem(List.of(dock("D1"), dock("D2"), dock("D3")),
                List.of(job("J1", 0, 60), job("J2", 0, 60), job("J3", 0, 30)), 2);

        Schedule schedule = scheduler.schedule(problem);

        assertThat(schedule.assignmentFor("J3")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(60)));
        assertThat(ScheduleValidator.validate(problem, schedule)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void usesAGapBetweenBookingsWhenTheJobFits(Scheduler scheduler) {
        Dock dock = new Dock("D1", Set.of(), ALL_DAY, List.of(new Interval(at(60), at(120))));
        var problem = new SchedulingProblem(List.of(dock), List.of(job("SHORT", 0, 45)), 1);

        assertThat(scheduler.schedule(problem).assignmentFor("SHORT"))
                .hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(0)));
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void doesNotRunPastClosingTime(Scheduler scheduler) {
        Dock shortDay = Dock.open("D1", Set.of(), new Interval(at(0), at(60)));
        var problem = new SchedulingProblem(List.of(shortDay), List.of(job("J1", 40, 30)), 1);

        assertThat(scheduler.schedule(problem).unscheduled())
                .containsExactly(new Unscheduled("J1", Unscheduled.Reason.NO_FEASIBLE_SLOT));
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void sameProblemInAnyInputOrderGivesTheSameSchedule(Scheduler scheduler) {
        var jobs = List.of(job("A", 0, 30), job("B", 0, 30), job("C", 5, 45), job("D", 5, 20),
                job("E", 10, 30, Priority.CRITICAL));
        var docks = List.of(dock("D1"), dock("D2"));
        var reversedJobs = jobs.reversed();
        var reversedDocks = docks.reversed();

        Schedule first = scheduler.schedule(new SchedulingProblem(docks, jobs, 2));
        Schedule second = scheduler.schedule(new SchedulingProblem(reversedDocks, reversedJobs, 2));

        assertThat(second).isEqualTo(first);
    }

    @ParameterizedTest
    @MethodSource("schedulers")
    void slotLengthEqualsLoadingTime(Scheduler scheduler) {
        var problem = new SchedulingProblem(List.of(dock("D1")), List.of(job("J1", 0, 37)), 1);

        assertThat(scheduler.schedule(problem).assignmentFor("J1")).hasValueSatisfying(
                a -> assertThat(Duration.between(a.start(), a.end())).isEqualTo(Duration.ofMinutes(37)));
    }
}
