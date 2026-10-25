package com.dockmind.dock.application;

import java.time.Duration;

/**
 * How long a dock is occupied for a given shipment. The same model as the benchmark workload
 * (10 min setup + 45 s per pallet), so production schedules and benchmarks use one assumption.
 * Real warehouses would calibrate this from history; that's a documented later improvement.
 */
public final class LoadingTimes {

    static final Duration SETUP = Duration.ofMinutes(10);
    static final Duration PER_PALLET = Duration.ofSeconds(45);

    private LoadingTimes() {
    }

    public static Duration forPallets(int pallets) {
        return SETUP.plus(PER_PALLET.multipliedBy(Math.max(pallets, 1)));
    }
}
