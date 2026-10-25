package com.dockmind.scheduling.recovery;

import com.dockmind.scheduling.model.Interval;

import java.time.Instant;
import java.util.Objects;

/** Something that invalidates part of a schedule. A sealed type: these are the only kinds. */
public sealed interface Disruption permits Disruption.LateArrival, Disruption.DockOutage {

    /** A truck will reach the gate later than planned. */
    record LateArrival(String jobId, Instant newArrival) implements Disruption {
        public LateArrival {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(newArrival, "newArrival");
        }
    }

    /** A dock can't be used for a period (breakdown, maintenance). */
    record DockOutage(String dockId, Interval period) implements Disruption {
        public DockOutage {
            Objects.requireNonNull(dockId, "dockId");
            Objects.requireNonNull(period, "period");
        }
    }
}
