package com.dockmind.dock.api;

import com.dockmind.dock.domain.ScheduleRun;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A computed schedule: who goes where and when, what couldn't be placed, and who computed it. */
public record ScheduleResponse(UUID runId, LocalDate date, String algorithm, int crewCapacity, Instant computedAt,
                               String computedBy, int scheduled, int unscheduled, double avgWaitMinutes,
                               List<Appointment> appointments, List<NotScheduled> notScheduled) {

    public record Appointment(UUID shipmentId, String reference, String dockCode, Instant start, Instant end) { }

    public record NotScheduled(UUID shipmentId, String reference, String reason) { }

    /** Must be called inside a transaction: the appointment lists load lazily. */
    public static ScheduleResponse from(ScheduleRun run) {
        return new ScheduleResponse(run.getId(), run.getScheduleDate(), run.getAlgorithm(), run.getCrewCapacity(),
                run.getComputedAt(), run.getComputedBy(), run.getScheduledCount(), run.getUnscheduledCount(),
                Math.round(run.getAvgWaitMinutes() * 10) / 10.0,
                run.getAppointments().stream()
                        .map(a -> new Appointment(a.getShipmentId(), a.getReference(), a.getDockCode(), a.getStartsAt(), a.getEndsAt()))
                        .sorted(Comparator.comparing(Appointment::start).thenComparing(Appointment::dockCode))
                        .toList(),
                run.getUnscheduled().stream()
                        .map(u -> new NotScheduled(u.getShipmentId(), u.getReference(), u.getReason()))
                        .sorted(Comparator.comparing(NotScheduled::reference))
                        .toList());
    }
}
