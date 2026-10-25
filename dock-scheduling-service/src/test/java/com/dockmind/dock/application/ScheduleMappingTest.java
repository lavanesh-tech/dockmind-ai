package com.dockmind.dock.application;

import com.dockmind.dock.domain.ShipmentView;
import com.dockmind.scheduling.model.Capability;
import com.dockmind.scheduling.model.Job;
import com.dockmind.scheduling.model.Priority;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** How stored shipments and docks become the engine's input. */
class ScheduleMappingTest {

    private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant END = Instant.parse("2026-11-02T10:00:00Z");

    private static ShipmentView view(Instant arrivedAt) {
        ShipmentView v = new ShipmentView(UUID.randomUUID());
        v.apply(UUID.randomUUID(), Instant.now(), "t-1", "PO-1", 20, List.of("REFRIGERATED"), "EXPEDITED",
                START, END, arrivedAt == null ? "BOOKED" : "ARRIVED", arrivedAt);
        return v;
    }

    @Test
    void bookedTruckIsAvailableFromItsWindowAndMustStartBeforeItCloses() {
        Job job = ScheduleService.toJob(view(null));

        assertThat(job.arrival()).isEqualTo(START);
        assertThat(job.latestStart()).isEqualTo(END);
        assertThat(job.priority()).isEqualTo(Priority.EXPEDITED);
        assertThat(job.requiredCapabilities()).containsExactly(Capability.REFRIGERATED);
        assertThat(job.loadingTime()).isEqualTo(Duration.ofMinutes(10).plusSeconds(20 * 45));
    }

    @Test
    void arrivedTruckIsAvailableFromItsActualArrival() {
        Instant arrived = Instant.parse("2026-11-02T08:40:00Z");

        assertThat(ScheduleService.toJob(view(arrived)).arrival()).isEqualTo(arrived);
    }

    @Test
    void truckThatArrivedAfterItsWindowHasNoDeadlineLeft() {
        Job job = ScheduleService.toJob(view(Instant.parse("2026-11-02T11:00:00Z")));

        assertThat(job.latestStart()).isNull();
    }

    @Test
    void dockHoursBecomeThatDaysInterval() {
        var dock = new com.dockmind.dock.domain.Dock("t-1", "D01", Set.of(Capability.HAZMAT),
                LocalTime.of(6, 0), LocalTime.of(22, 0));

        var engineDock = ScheduleService.toEngineDock(dock, LocalDate.of(2026, 11, 2));

        assertThat(engineDock.id()).isEqualTo("D01");
        assertThat(engineDock.openHours().start()).isEqualTo(Instant.parse("2026-11-02T06:00:00Z"));
        assertThat(engineDock.openHours().end()).isEqualTo(Instant.parse("2026-11-02T22:00:00Z"));
        assertThat(engineDock.capabilities()).containsExactly(Capability.HAZMAT);
    }
}
