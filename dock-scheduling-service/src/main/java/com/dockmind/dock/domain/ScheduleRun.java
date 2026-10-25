package com.dockmind.dock.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One computed schedule for one tenant and day. Runs are never updated or deleted: computing
 * again adds a new run, so the full history of scheduling decisions (who, when, what result)
 * is kept as an audit trail.
 */
@Entity
@Table(name = "schedule_run")
public class ScheduleRun {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "schedule_date", nullable = false, updatable = false)
    private LocalDate scheduleDate;

    @Column(nullable = false, updatable = false)
    private String algorithm;

    @Column(name = "crew_capacity", nullable = false, updatable = false)
    private int crewCapacity;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    @Column(name = "computed_by", nullable = false, updatable = false)
    private String computedBy;

    @Column(name = "scheduled_count", nullable = false, updatable = false)
    private int scheduledCount;

    @Column(name = "unscheduled_count", nullable = false, updatable = false)
    private int unscheduledCount;

    @Column(name = "avg_wait_minutes", nullable = false, updatable = false)
    private double avgWaitMinutes;

    // LAZY on purpose: Hibernate can't eagerly join two lists at once ("multiple bags").
    // Responses are built inside the service transaction, where lazy loading works.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "schedule_appointment", joinColumns = @JoinColumn(name = "run_id"))
    private List<AppointmentRow> appointments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "schedule_unscheduled", joinColumns = @JoinColumn(name = "run_id"))
    private List<UnscheduledRow> unscheduled = new ArrayList<>();

    protected ScheduleRun() {
    }

    public ScheduleRun(String tenantId, LocalDate scheduleDate, String algorithm, int crewCapacity, Instant computedAt,
                       String computedBy, double avgWaitMinutes, List<AppointmentRow> appointments,
                       List<UnscheduledRow> unscheduled) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.scheduleDate = scheduleDate;
        this.algorithm = algorithm;
        this.crewCapacity = crewCapacity;
        this.computedAt = computedAt;
        this.computedBy = computedBy;
        this.avgWaitMinutes = avgWaitMinutes;
        this.appointments = new ArrayList<>(appointments);
        this.unscheduled = new ArrayList<>(unscheduled);
        this.scheduledCount = appointments.size();
        this.unscheduledCount = unscheduled.size();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public String getAlgorithm() { return algorithm; }
    public int getCrewCapacity() { return crewCapacity; }
    public Instant getComputedAt() { return computedAt; }
    public String getComputedBy() { return computedBy; }
    public int getScheduledCount() { return scheduledCount; }
    public int getUnscheduledCount() { return unscheduledCount; }
    public double getAvgWaitMinutes() { return avgWaitMinutes; }
    public List<AppointmentRow> getAppointments() { return List.copyOf(appointments); }
    public List<UnscheduledRow> getUnscheduled() { return List.copyOf(unscheduled); }
}
