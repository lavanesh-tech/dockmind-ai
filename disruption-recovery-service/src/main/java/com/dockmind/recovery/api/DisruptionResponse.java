package com.dockmind.recovery.api;

import com.dockmind.recovery.domain.Disruption;
import com.dockmind.recovery.domain.DisruptionStatus;
import com.dockmind.recovery.domain.DisruptionType;
import com.dockmind.recovery.domain.StoredPlan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A disruption, the plans to choose from (best first) and the decision, if made. */
public record DisruptionResponse(UUID id, DisruptionType type, LocalDate scheduleDate, UUID shipmentId, Instant newArrival,
                                 String dockCode, Instant outageStart, Instant outageEnd, Instant decisionTime,
                                 DisruptionStatus status, String reportedBy, Instant reportedAt, String decidedBy,
                                 Instant decidedAt, String decisionNote, String appliedStrategy, UUID appliedRunId,
                                 List<PlanSummary> plans) {

    /**
     * @param recommended   the first-ranked plan
     * @param requiresAdmin moves more appointments than a DISPATCHER may approve
     */
    public record PlanSummary(String strategy, int rank, boolean recommended, int moved, int scheduled, int unscheduled,
                              int urgentUnscheduled, double avgWaitMinutes, boolean requiresAdmin) { }

    public static DisruptionResponse of(Disruption d, List<StoredPlan> plans, int adminThreshold) {
        return new DisruptionResponse(d.getId(), d.getType(), d.getScheduleDate(), d.getShipmentId(), d.getNewArrival(),
                d.getDockCode(), d.getOutageStart(), d.getOutageEnd(), d.getDecisionTime(), d.getStatus(), d.getReportedBy(),
                d.getReportedAt(), d.getDecidedBy(), d.getDecidedAt(), d.getDecisionNote(), d.getAppliedStrategy(),
                d.getAppliedRunId(),
                plans.stream().map(p -> new PlanSummary(p.getStrategy(), p.getRank(), p.getRank() == 1, p.getMoved(),
                        p.getScheduled(), p.getUnscheduled(), p.getUrgentUnscheduled(),
                        Math.round(p.getAvgWaitMinutes() * 10) / 10.0, p.getMoved() > adminThreshold)).toList());
    }
}
