package com.dockmind.dock.application;

import com.dockmind.common.web.ApiException;
import com.dockmind.dock.api.ApplyScheduleRequest;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.SchedulingProblem;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A proposed plan may only make the problem stricter, never loosen the rules it is checked against. */
class ApplyOverridesTest {

    private static final Instant OPEN = Instant.parse("2026-11-02T06:00:00Z");
    private static final UUID TRUCK = UUID.randomUUID();
    private static final Instant ARRIVAL = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-02T09:00:00Z");

    private static final SchedulingProblem PROBLEM = new SchedulingProblem(
            List.of(new Dock("D1", Set.of(), new Interval(OPEN, OPEN.plus(Duration.ofHours(12))), List.of())),
            List.of(new Job(TRUCK.toString(), Set.of(), Priority.STANDARD, ARRIVAL, Duration.ofMinutes(30), DEADLINE)),
            2);

    private static ApplyScheduleRequest request(List<ApplyScheduleRequest.BlockedInput> blocked,
                                                List<ApplyScheduleRequest.ArrivalOverride> overrides) {
        return new ApplyScheduleRequest(UUID.randomUUID(), "MINIMAL_CHANGE", "test", List.of(), List.of(), blocked, overrides);
    }

    @Test
    void outageIsAddedToTheDock() {
        Instant from = OPEN.plus(Duration.ofHours(2));
        SchedulingProblem result = ApplyOverridesTestSupport.apply(PROBLEM, request(
                List.of(new ApplyScheduleRequest.BlockedInput("D1", from, from.plus(Duration.ofHours(1)))), null));

        assertThat(result.docksById().get("D1").blocked()).containsExactly(new Interval(from, from.plus(Duration.ofHours(1))));
    }

    @Test
    void laterArrivalKeepingTheDeadlineIsAccepted() {
        Instant later = ARRIVAL.plus(Duration.ofMinutes(20));
        SchedulingProblem result = ApplyOverridesTestSupport.apply(PROBLEM, request(null,
                List.of(new ApplyScheduleRequest.ArrivalOverride(TRUCK, later, DEADLINE))));

        assertThat(result.jobsById().get(TRUCK.toString()).arrival()).isEqualTo(later);
    }

    @Test
    void deadlineCanBeDroppedOnlyOnceItCanNoLongerBeMet() {
        Instant afterDeadline = DEADLINE.plus(Duration.ofMinutes(5));
        SchedulingProblem result = ApplyOverridesTestSupport.apply(PROBLEM, request(null,
                List.of(new ApplyScheduleRequest.ArrivalOverride(TRUCK, afterDeadline, null))));
        assertThat(result.jobsById().get(TRUCK.toString()).latestStart()).isNull();

        assertThatThrownBy(() -> ApplyOverridesTestSupport.apply(PROBLEM, request(null,
                List.of(new ApplyScheduleRequest.ArrivalOverride(TRUCK, ARRIVAL, null)))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void earlierArrivalUnknownTruckAndUnknownDockAreRejected() {
        assertThatThrownBy(() -> ApplyOverridesTestSupport.apply(PROBLEM, request(null,
                List.of(new ApplyScheduleRequest.ArrivalOverride(TRUCK, ARRIVAL.minusSeconds(60), DEADLINE)))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ApplyOverridesTestSupport.apply(PROBLEM, request(null,
                List.of(new ApplyScheduleRequest.ArrivalOverride(UUID.randomUUID(), ARRIVAL, DEADLINE)))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ApplyOverridesTestSupport.apply(PROBLEM, request(
                List.of(new ApplyScheduleRequest.BlockedInput("NOPE", OPEN, OPEN.plusSeconds(60))), null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void movingTheDeadlineLaterIsRejected() {
        assertThatThrownBy(() -> ApplyOverridesTestSupport.apply(PROBLEM, request(null,
                List.of(new ApplyScheduleRequest.ArrivalOverride(TRUCK, ARRIVAL, DEADLINE.plusSeconds(3600))))))
                .isInstanceOf(ApiException.class);
    }

    /** Indirection so the assertions read as "apply the plan's overrides". */
    private static final class ApplyOverridesTestSupport {
        static SchedulingProblem apply(SchedulingProblem problem, ApplyScheduleRequest request) {
            return ScheduleService.withOverrides(problem, request);
        }
    }
}
