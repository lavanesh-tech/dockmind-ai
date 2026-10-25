package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dockmind.scheduling.TestData.at;
import static com.dockmind.scheduling.TestData.dock;
import static com.dockmind.scheduling.TestData.job;
import static com.dockmind.scheduling.TestData.jobWithDeadline;
import static org.assertj.core.api.Assertions.assertThat;

/** The two decisions where DockMind differs from FCFS, each shown on a tiny scenario. */
class PriorityAwareSchedulerTest {

    private final Scheduler fcfs = new FcfsScheduler();
    private final Scheduler dockmind = new PriorityAwareScheduler();

    @Test
    void criticalTruckGoesBeforeAStandardTruckThatArrivedEarlier() {
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("STANDARD", 0, 60), job("CRITICAL", 5, 30, Priority.CRITICAL)), 1);

        Schedule byFcfs = fcfs.schedule(problem);
        Schedule byDockmind = dockmind.schedule(problem);

        assertThat(byFcfs.assignmentFor("CRITICAL")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(60)));
        assertThat(byDockmind.assignmentFor("CRITICAL")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(5)));
    }

    @Test
    void tighterDeadlineGoesFirstAndBothTrucksAreServed() {
        // FCFS serves the early truck first, which makes the second one miss its deadline.
        var problem = new SchedulingProblem(List.of(dock("D1")),
                List.of(job("FLEXIBLE", 0, 60), jobWithDeadline("TIGHT", 10, 30, 20)), 1);

        assertThat(fcfs.schedule(problem).unscheduled()).extracting(u -> u.jobId()).containsExactly("TIGHT");

        Schedule byDockmind = dockmind.schedule(problem);
        assertThat(byDockmind.unscheduled()).isEmpty();
        assertThat(byDockmind.assignmentFor("TIGHT")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(10)));
        assertThat(byDockmind.assignmentFor("FLEXIBLE")).hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(40)));
    }

    @Test
    void keepsTheRefrigeratedDockFreeForTrucksThatNeedIt() {
        // D1 (refrigerated) sorts first, so FCFS puts the dry truck there and the cold truck waits.
        var problem = new SchedulingProblem(
                List.of(dock("D1-REEFER", Capability.REFRIGERATED), dock("D2")),
                List.of(job("DRY", 0, 60), job("D2-BUSY", 0, 10),
                        job("COLD", 5, 30, Priority.STANDARD, Capability.REFRIGERATED)), 3);

        assertThat(fcfs.schedule(problem).assignmentFor("COLD"))
                .hasValueSatisfying(a -> assertThat(a.start()).isAfter(at(5)));

        assertThat(dockmind.schedule(problem).assignmentFor("COLD"))
                .hasValueSatisfying(a -> assertThat(a.start()).isEqualTo(at(5)));
    }
}
