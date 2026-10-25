package com.dockmind.scheduling.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Everything a scheduler needs: the docks, the jobs, and how many loading crews exist.
 *
 * <p>{@code crewCapacity} is a warehouse-wide resource limit: at most that many docks can be
 * loading at the same moment, however many docks are free. This models forklift crews.
 */
public record SchedulingProblem(List<Dock> docks, List<Job> jobs, int crewCapacity) {

    public SchedulingProblem {
        docks = List.copyOf(Objects.requireNonNull(docks, "docks"));
        jobs = List.copyOf(Objects.requireNonNull(jobs, "jobs"));
        if (crewCapacity < 1) {
            throw new IllegalArgumentException("crewCapacity must be at least 1");
        }
        requireUniqueIds(docks.stream().map(Dock::id).toList(), "dock");
        requireUniqueIds(jobs.stream().map(Job::id).toList(), "job");
    }

    public Optional<Dock> dock(String id) {
        return docks.stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public Map<String, Job> jobsById() {
        return jobs.stream().collect(Collectors.toMap(Job::id, Function.identity()));
    }

    public Map<String, Dock> docksById() {
        return docks.stream().collect(Collectors.toMap(Dock::id, Function.identity()));
    }

    private static void requireUniqueIds(List<String> ids, String kind) {
        var seen = new HashSet<String>();
        for (String id : ids) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Duplicate " + kind + " id: " + id);
            }
        }
    }
}
