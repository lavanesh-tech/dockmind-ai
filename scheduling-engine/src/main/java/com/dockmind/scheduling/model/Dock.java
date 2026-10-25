package com.dockmind.scheduling.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A loading dock.
 *
 * @param id           unique dock identifier
 * @param capabilities equipment this dock has
 * @param openHours    when the dock can be used at all
 * @param blocked      periods the dock is unavailable (maintenance, outages); sorted on construction
 */
public record Dock(String id, Set<Capability> capabilities, Interval openHours, List<Interval> blocked) {

    public Dock {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(openHours, "openHours");
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(capabilities));
        blocked = blocked == null ? List.of() : blocked.stream().sorted().toList();
    }

    public static Dock open(String id, Set<Capability> capabilities, Interval openHours) {
        return new Dock(id, capabilities, openHours, List.of());
    }

    public boolean canServe(Job job) {
        return capabilities.containsAll(job.requiredCapabilities());
    }

    /** Capabilities this dock has that the job doesn't need: what serving the job "wastes". */
    public int extraCapabilitiesFor(Job job) {
        return (int) capabilities.stream().filter(c -> !job.requiredCapabilities().contains(c)).count();
    }

    /** Returns a copy of this dock with an extra blocked period, e.g. a reported outage. */
    public Dock withBlocked(Interval outage) {
        List<Interval> all = new java.util.ArrayList<>(blocked);
        all.add(outage);
        return new Dock(id, capabilities, openHours, all);
    }
}
