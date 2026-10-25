package com.dockmind.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * One recovery alternative for a disruption, with the comparison numbers and the exact request
 * that will be sent to Dock Scheduling if it is approved. Plans never change after creation.
 */
@Entity
@Table(name = "recovery_plan")
public class StoredPlan implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "disruption_id", nullable = false, updatable = false)
    private UUID disruptionId;

    @Column(nullable = false, updatable = false)
    private String strategy;

    @Column(name = "plan_rank", nullable = false, updatable = false)
    private int rank;

    @Column(nullable = false, updatable = false)
    private int moved;

    @Column(nullable = false, updatable = false)
    private int scheduled;

    @Column(nullable = false, updatable = false)
    private int unscheduled;

    @Column(name = "urgent_unscheduled", nullable = false, updatable = false)
    private int urgentUnscheduled;

    @Column(name = "avg_wait_minutes", nullable = false, updatable = false)
    private double avgWaitMinutes;

    @Column(name = "apply_request", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String applyRequest;

    protected StoredPlan() {
    }

    public StoredPlan(UUID disruptionId, String strategy, int rank, int moved, int scheduled, int unscheduled,
                      int urgentUnscheduled, double avgWaitMinutes, String applyRequest) {
        this.id = UUID.randomUUID();
        this.disruptionId = disruptionId;
        this.strategy = strategy;
        this.rank = rank;
        this.moved = moved;
        this.scheduled = scheduled;
        this.unscheduled = unscheduled;
        this.urgentUnscheduled = urgentUnscheduled;
        this.avgWaitMinutes = avgWaitMinutes;
        this.applyRequest = applyRequest;
    }

    @Override
    public UUID getId() { return id; }

    /** Plans are only ever inserted, so tell Spring Data to INSERT without checking first. */
    @Override
    public boolean isNew() {
        return true;
    }

    public UUID getDisruptionId() { return disruptionId; }
    public String getStrategy() { return strategy; }
    public int getRank() { return rank; }
    public int getMoved() { return moved; }
    public int getScheduled() { return scheduled; }
    public int getUnscheduled() { return unscheduled; }
    public int getUrgentUnscheduled() { return urgentUnscheduled; }
    public double getAvgWaitMinutes() { return avgWaitMinutes; }
    public String getApplyRequest() { return applyRequest; }
}
