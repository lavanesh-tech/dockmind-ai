package com.dockmind.scheduling.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The output of a scheduler. Lists are sorted on construction, so two schedules with the
 * same content are {@code equals}, whatever order they were built in.
 */
public record Schedule(String algorithm, List<Assignment> assignments, List<Unscheduled> unscheduled) {

    public Schedule {
        Objects.requireNonNull(algorithm, "algorithm");
        assignments = assignments.stream().sorted(Assignment.ORDER).toList();
        unscheduled = unscheduled.stream().sorted(Comparator.comparing(Unscheduled::jobId)).toList();
    }

    public Optional<Assignment> assignmentFor(String jobId) {
        return assignments.stream().filter(a -> a.jobId().equals(jobId)).findFirst();
    }
}
