package com.dockmind.scheduling.model;

/** A job the scheduler could not place, and why. Every job ends up either assigned or here. */
public record Unscheduled(String jobId, Reason reason) {

    public enum Reason {
        /** No dock has all the required capabilities. */
        NO_COMPATIBLE_DOCK,
        /** Compatible docks exist, but no slot satisfies arrival, deadline, open hours and crew limits. */
        NO_FEASIBLE_SLOT
    }
}
