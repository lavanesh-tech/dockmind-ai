package com.dockmind.scheduling.validation;

import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.model.Unscheduled;
import com.dockmind.scheduling.validation.Violation.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.dockmind.scheduling.TestData.ALL_DAY;
import static com.dockmind.scheduling.TestData.at;
import static com.dockmind.scheduling.TestData.dock;
import static com.dockmind.scheduling.TestData.job;
import static com.dockmind.scheduling.TestData.jobWithDeadline;
import static org.assertj.core.api.Assertions.assertThat;

/** Each test hands the validator a hand-made, deliberately broken schedule. */
class ScheduleValidatorTest {

    private static final SchedulingProblem PROBLEM = new SchedulingProblem(
            List.of(dock("D1"), dock("D2"), new Dock("D3", Set.of(), ALL_DAY, List.of(new Interval(at(0), at(60))))),
            List.of(job("J1", 0, 30), job("J2", 0, 30), job("COLD", 0, 30,
                    com.dockmind.scheduling.model.Priority.STANDARD, Capability.REFRIGERATED),
                    jobWithDeadline("LATE", 0, 30, 10)),
            1);

    private static List<Rule> rules(Assignment... assignments) {
        List<String> assigned = java.util.Arrays.stream(assignments).map(Assignment::jobId).toList();
        List<Unscheduled> rest = PROBLEM.jobs().stream()
                .filter(j -> !assigned.contains(j.id()))
                .map(j -> new Unscheduled(j.id(), Unscheduled.Reason.NO_FEASIBLE_SLOT))
                .toList();
        return ScheduleValidator.validate(PROBLEM, new Schedule("test", List.of(assignments), rest))
                .stream().map(Violation::rule).toList();
    }

    private static Assignment a(String job, String dock, int start, int end) {
        return new Assignment(job, dock, at(start), at(end));
    }

    @Test
    void acceptsAValidSchedule() {
        assertThat(rules(a("J1", "D1", 0, 30))).isEmpty();
    }

    @Test
    void detectsOverlapOnOneDock() {
        assertThat(rules(a("J1", "D1", 0, 30), a("J2", "D1", 20, 50))).contains(Rule.DOCK_OVERLAP);
    }

    @Test
    void detectsCrewCapacityExceeded() {
        // Different docks, same time, but only one crew in this problem.
        assertThat(rules(a("J1", "D1", 0, 30), a("J2", "D2", 10, 40))).contains(Rule.CREW_CAPACITY_EXCEEDED);
    }

    @Test
    void backToBackIsNotAnOverlap() {
        assertThat(rules(a("J1", "D1", 0, 30), a("J2", "D1", 30, 60))).isEmpty();
    }

    @Test
    void detectsMissingCapability() {
        assertThat(rules(a("COLD", "D1", 0, 30))).containsExactly(Rule.CAPABILITY_MISMATCH);
    }

    @Test
    void detectsStartBeforeArrival() {
        var problem = new SchedulingProblem(List.of(dock("D1")), List.of(job("J1", 30, 30)), 1);
        var schedule = new Schedule("test", List.of(a("J1", "D1", 0, 30)), List.of());

        assertThat(ScheduleValidator.validate(problem, schedule)).extracting(Violation::rule)
                .containsExactly(Rule.BEFORE_ARRIVAL);
    }

    @Test
    void detectsMissedDeadline() {
        assertThat(rules(a("LATE", "D1", 20, 50))).containsExactly(Rule.AFTER_LATEST_START);
    }

    @Test
    void detectsBookingDuringAnOutage() {
        assertThat(rules(a("J1", "D3", 30, 60))).containsExactly(Rule.DOCK_BLOCKED);
    }

    @Test
    void detectsWrongDuration() {
        assertThat(rules(a("J1", "D1", 0, 20))).containsExactly(Rule.WRONG_DURATION);
    }

    @Test
    void detectsUnknownDock() {
        assertThat(rules(a("J1", "NOPE", 0, 30))).containsExactly(Rule.UNKNOWN_DOCK);
    }

    @Test
    void detectsJobsThatAreMissingOrDuplicated() {
        var schedule = new Schedule("test", List.of(a("J1", "D1", 0, 30), a("J1", "D2", 60, 90)), List.of());

        List<Rule> found = ScheduleValidator.validate(PROBLEM, schedule).stream().map(Violation::rule).toList();

        assertThat(found).contains(Rule.DUPLICATE_JOB, Rule.MISSING_JOB);
    }

    @Test
    void detectsOutsideOpenHours() {
        var problem = new SchedulingProblem(List.of(Dock.open("D1", Set.of(), new Interval(at(0), at(60)))),
                List.of(job("J1", 0, 30)), 1);
        var schedule = new Schedule("test", List.of(a("J1", "D1", 45, 75)), List.of());

        assertThat(ScheduleValidator.validate(problem, schedule)).extracting(Violation::rule)
                .containsExactly(Rule.OUTSIDE_OPEN_HOURS);
    }
}
