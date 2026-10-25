package com.dockmind.scheduling.benchmark;

import com.dockmind.scheduling.model.SchedulingProblem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkTest {

    @Test
    void sameSeedGivesTheSameWorkload() {
        assertThat(WorkloadGenerator.generate(7, 100)).isEqualTo(WorkloadGenerator.generate(7, 100));
    }

    @Test
    void differentSeedsGiveDifferentWorkloads() {
        assertThat(WorkloadGenerator.generate(7, 100)).isNotEqualTo(WorkloadGenerator.generate(8, 100));
    }

    @Test
    void generatedJobsAreAllServableAndInsideOpeningHours() {
        SchedulingProblem problem = WorkloadGenerator.generate(3, 300);

        assertThat(problem.jobs()).allSatisfy(job -> {
            assertThat(problem.docks()).anyMatch(d -> d.canServe(job));
            assertThat(job.arrival()).isBetween(WorkloadGenerator.OPEN.start(), WorkloadGenerator.OPEN.end());
        });
    }

    @Test
    void smallRunProducesRowsForEveryAlgorithmWithNoViolations() {
        List<BenchmarkRunner.Row> rows = BenchmarkRunner.run(new int[] {60}, new long[] {1, 2});

        assertThat(rows).hasSize(6);
        assertThat(rows).allSatisfy(r -> assertThat(r.metrics().violations()).isZero());
        assertThat(BenchmarkRunner.toCsv(rows).lines()).hasSize(7);
        assertThat(BenchmarkRunner.toMarkdown(rows)).contains("| 60 | FCFS |", "| 60 | PRIORITY_AWARE |", "| 60 | DOCKMIND_DISPATCH |");
    }
}
