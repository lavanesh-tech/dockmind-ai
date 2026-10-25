package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.benchmark.WorkloadGenerator;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.validation.ScheduleValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-style tests: instead of one hand-picked example, check rules that must hold for
 * every input, across many generated warehouse days of different sizes.
 */
class SchedulerPropertyTest {

    static Stream<Arguments> cases() {
        return LongStream.rangeClosed(1, 40).boxed().flatMap(seed -> Stream.of(
                Arguments.of(new FcfsScheduler(), seed, (int) (50 + seed * 8)),
                Arguments.of(new PriorityAwareScheduler(), seed, (int) (50 + seed * 8)),
                Arguments.of(new DispatchScheduler(), seed, (int) (50 + seed * 8))));
    }

    @ParameterizedTest(name = "{0} seed={1} jobs={2}")
    @MethodSource("cases")
    void everyScheduleIsValidAndAccountsForEveryJob(Scheduler scheduler, long seed, int jobs) {
        SchedulingProblem problem = WorkloadGenerator.generate(seed, jobs);

        Schedule schedule = scheduler.schedule(problem);

        assertThat(ScheduleValidator.validate(problem, schedule)).isEmpty();
        assertThat(schedule.assignments().size() + schedule.unscheduled().size()).isEqualTo(jobs);
    }

    @ParameterizedTest(name = "{0} seed={1} jobs={2}")
    @MethodSource("cases")
    void inputOrderNeverChangesTheResult(Scheduler scheduler, long seed, int jobs) {
        SchedulingProblem problem = WorkloadGenerator.generate(seed, jobs);
        List<com.dockmind.scheduling.model.Job> shuffled = new ArrayList<>(problem.jobs());
        Collections.shuffle(shuffled, new Random(seed * 31));
        List<com.dockmind.scheduling.model.Dock> docks = new ArrayList<>(problem.docks());
        Collections.reverse(docks);

        Schedule original = scheduler.schedule(problem);
        Schedule reordered = scheduler.schedule(new SchedulingProblem(docks, shuffled, problem.crewCapacity()));

        assertThat(reordered).isEqualTo(original);
    }
}
