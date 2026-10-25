package com.dockmind.scheduling.metrics;

import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.Dock;
import com.dockmind.scheduling.model.Interval;
import com.dockmind.scheduling.model.Priority;
import com.dockmind.scheduling.model.Schedule;
import com.dockmind.scheduling.model.SchedulingProblem;
import com.dockmind.scheduling.model.Unscheduled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.dockmind.scheduling.TestData.at;
import static com.dockmind.scheduling.TestData.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScheduleMetricsTest {

    @Test
    void nearestRankPercentile() {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0,
                11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 100.0);

        assertThat(ScheduleMetrics.percentile(values, 95)).isEqualTo(19.0); // rank ceil(0.95*20)=19
        assertThat(ScheduleMetrics.percentile(List.of(7.0), 95)).isEqualTo(7.0);
        assertThat(ScheduleMetrics.percentile(List.of(), 95)).isZero();
    }

    @Test
    void computesWaitUtilizationAndUrgentFigures() {
        // One dock open 2 h with a 30 min outage → 90 min available.
        Dock dock = new Dock("D1", Set.of(), new Interval(at(0), at(120)), List.of(new Interval(at(90), at(120))));
        var problem = new SchedulingProblem(List.of(dock),
                List.of(job("A", 0, 30), job("B", 0, 30, Priority.CRITICAL), job("C", 0, 30, Priority.EXPEDITED)), 1);
        var schedule = new Schedule("test",
                List.of(new Assignment("A", "D1", at(0), at(30)), new Assignment("B", "D1", at(30), at(60))),
                List.of(new Unscheduled("C", Unscheduled.Reason.NO_FEASIBLE_SLOT)));

        ScheduleMetrics m = ScheduleMetrics.of(problem, schedule);

        assertThat(m.scheduled()).isEqualTo(2);
        assertThat(m.unscheduled()).isEqualTo(1);
        assertThat(m.avgWaitMinutes()).isEqualTo(15.0);      // waits 0 and 30
        assertThat(m.urgentAvgWaitMinutes()).isEqualTo(30.0); // only B is urgent and scheduled
        assertThat(m.urgentUnscheduled()).isEqualTo(1);       // C
        assertThat(m.dockUtilization()).isCloseTo(60.0 / 90.0, within(1e-9));
        assertThat(m.violations()).isZero();
    }
}
