package com.dockmind.scheduling.engine;

import com.dockmind.scheduling.model.Assignment;
import com.dockmind.scheduling.model.SchedulingProblem;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks that fixed appointments refer to real jobs, each at most once. */
final class FixedAppointments {

    private FixedAppointments() {
    }

    static Set<String> idsOf(SchedulingProblem problem, List<Assignment> fixed) {
        Map<String, ?> jobs = problem.jobsById();
        Set<String> ids = new HashSet<>();
        for (Assignment a : fixed) {
            if (!jobs.containsKey(a.jobId())) {
                throw new IllegalArgumentException("Fixed appointment for unknown job " + a.jobId());
            }
            if (!ids.add(a.jobId())) {
                throw new IllegalArgumentException("Job " + a.jobId() + " is fixed more than once");
            }
        }
        return ids;
    }
}
