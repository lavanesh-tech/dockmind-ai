package com.dockmind.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A reported disruption and what was decided about it. Created PENDING_APPROVAL with its plans;
 * a person then approves one plan (applied to the schedule) or rejects them all.
 */
@Entity
@Table(name = "disruption")
public class Disruption {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "schedule_date", nullable = false, updatable = false)
    private LocalDate scheduleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DisruptionType type;

    @Column(name = "shipment_id", updatable = false)
    private UUID shipmentId;

    @Column(name = "new_arrival", updatable = false)
    private Instant newArrival;

    @Column(name = "dock_code", updatable = false)
    private String dockCode;

    @Column(name = "outage_start", updatable = false)
    private Instant outageStart;

    @Column(name = "outage_end", updatable = false)
    private Instant outageEnd;

    @Column(name = "decision_time", nullable = false, updatable = false)
    private Instant decisionTime;

    @Column(name = "base_run_id", nullable = false, updatable = false)
    private UUID baseRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisruptionStatus status;

    @Column(name = "reported_by", nullable = false, updatable = false)
    private String reportedBy;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "applied_strategy")
    private String appliedStrategy;

    @Column(name = "applied_run_id")
    private UUID appliedRunId;

    @Version
    private Long version;

    protected Disruption() {
    }

    private Disruption(String tenantId, LocalDate scheduleDate, DisruptionType type, Instant decisionTime,
                       UUID baseRunId, String reportedBy, Instant reportedAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.scheduleDate = scheduleDate;
        this.type = type;
        this.decisionTime = decisionTime;
        this.baseRunId = baseRunId;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
        this.status = DisruptionStatus.PENDING_APPROVAL;
    }

    public static Disruption lateArrival(String tenantId, LocalDate date, UUID shipmentId, Instant newArrival,
                                         Instant decisionTime, UUID baseRunId, String reportedBy, Instant now) {
        Disruption d = new Disruption(tenantId, date, DisruptionType.LATE_ARRIVAL, decisionTime, baseRunId, reportedBy, now);
        d.shipmentId = shipmentId;
        d.newArrival = newArrival;
        return d;
    }

    public static Disruption dockOutage(String tenantId, LocalDate date, String dockCode, Instant start, Instant end,
                                        Instant decisionTime, UUID baseRunId, String reportedBy, Instant now) {
        Disruption d = new Disruption(tenantId, date, DisruptionType.DOCK_OUTAGE, decisionTime, baseRunId, reportedBy, now);
        d.dockCode = dockCode;
        d.outageStart = start;
        d.outageEnd = end;
        return d;
    }

    public boolean isPending() {
        return status == DisruptionStatus.PENDING_APPROVAL;
    }

    public void markApplied(String by, Instant at, String strategy, UUID runId, String note) {
        decide(DisruptionStatus.APPLIED, by, at, note);
        this.appliedStrategy = strategy;
        this.appliedRunId = runId;
    }

    public void markRejected(String by, Instant at, String note) {
        decide(DisruptionStatus.REJECTED, by, at, note);
    }

    public void markFailed(String by, Instant at, String note) {
        decide(DisruptionStatus.FAILED, by, at, note);
    }

    private void decide(DisruptionStatus next, String by, Instant at, String note) {
        if (!isPending()) {
            throw new IllegalStateException("Disruption " + id + " is already " + status);
        }
        this.status = next;
        this.decidedBy = by;
        this.decidedAt = at;
        this.decisionNote = note;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public DisruptionType getType() { return type; }
    public UUID getShipmentId() { return shipmentId; }
    public Instant getNewArrival() { return newArrival; }
    public String getDockCode() { return dockCode; }
    public Instant getOutageStart() { return outageStart; }
    public Instant getOutageEnd() { return outageEnd; }
    public Instant getDecisionTime() { return decisionTime; }
    public UUID getBaseRunId() { return baseRunId; }
    public DisruptionStatus getStatus() { return status; }
    public String getReportedBy() { return reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public String getAppliedStrategy() { return appliedStrategy; }
    public UUID getAppliedRunId() { return appliedRunId; }
    public Long getVersion() { return version; }
}
