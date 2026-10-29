package com.moveinsync.evfleet.explain;

import com.moveinsync.evfleet.planner.VehicleTimeline;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Top-level, immutable Phase 4 explanation and quality result for a planning run.
 *
 * <p>Produced by {@link PlanExplanationService}. Contains:
 * <ul>
 *   <li>Per-trip assignment explanations</li>
 *   <li>Per-session charging explanations</li>
 *   <li>Per-vehicle timelines (reusing the planner's VehicleTimeline)</li>
 *   <li>Cost breakdown</li>
 *   <li>Plan quality report</li>
 *   <li>Lower-bound results (backup trips and electricity cost)</li>
 * </ul>
 *
 * <p>The plan and input are not modified by producing this object.
 */
public final class PlanExplanation {

    private final String planningRunId;
    private final List<AssignmentExplanation> assignmentExplanations;
    private final List<ChargingExplanation> chargingExplanations;
    private final Map<String, VehicleTimeline> vehicleTimelines;
    private final CostBreakdown costBreakdown;
    private final PlanQualityReport qualityReport;
    private final LowerBoundResult backupTripLowerBound;
    private final LowerBoundResult electricityCostLowerBound;
    private final Duration generationDuration;

    private PlanExplanation(Builder b) {
        this.planningRunId             = Objects.requireNonNull(b.planningRunId, "planningRunId");
        this.assignmentExplanations    = b.assignmentExplanations != null
                ? List.copyOf(b.assignmentExplanations) : Collections.emptyList();
        this.chargingExplanations      = b.chargingExplanations != null
                ? List.copyOf(b.chargingExplanations) : Collections.emptyList();
        this.vehicleTimelines          = b.vehicleTimelines != null
                ? Map.copyOf(b.vehicleTimelines) : Collections.emptyMap();
        this.costBreakdown             = Objects.requireNonNull(b.costBreakdown, "costBreakdown");
        this.qualityReport             = Objects.requireNonNull(b.qualityReport, "qualityReport");
        this.backupTripLowerBound      = Objects.requireNonNull(b.backupTripLowerBound, "backupTripLowerBound");
        this.electricityCostLowerBound = Objects.requireNonNull(b.electricityCostLowerBound, "electricityCostLowerBound");
        this.generationDuration        = b.generationDuration != null ? b.generationDuration : Duration.ZERO;
    }

    public String getPlanningRunId()                              { return planningRunId; }
    public List<AssignmentExplanation> getAssignmentExplanations() { return assignmentExplanations; }
    public List<ChargingExplanation> getChargingExplanations()   { return chargingExplanations; }
    public Map<String, VehicleTimeline> getVehicleTimelines()    { return vehicleTimelines; }
    public CostBreakdown getCostBreakdown()                       { return costBreakdown; }
    public PlanQualityReport getQualityReport()                   { return qualityReport; }
    public LowerBoundResult getBackupTripLowerBound()             { return backupTripLowerBound; }
    public LowerBoundResult getElectricityCostLowerBound()        { return electricityCostLowerBound; }
    public Duration getGenerationDuration()                       { return generationDuration; }

    @Override
    public String toString() {
        return String.format(
                "PlanExplanation[runId=%s, trips=%d (EV=%d, backup=%d), sessions=%d, generatedIn=%dms]",
                planningRunId,
                assignmentExplanations.size(),
                assignmentExplanations.stream().filter(AssignmentExplanation::isEvAssignment).count(),
                assignmentExplanations.stream().filter(AssignmentExplanation::isBackupAssignment).count(),
                chargingExplanations.size(),
                generationDuration.toMillis());
    }

    public static final class Builder {
        private String planningRunId;
        private List<AssignmentExplanation> assignmentExplanations;
        private List<ChargingExplanation> chargingExplanations;
        private Map<String, VehicleTimeline> vehicleTimelines;
        private CostBreakdown costBreakdown;
        private PlanQualityReport qualityReport;
        private LowerBoundResult backupTripLowerBound;
        private LowerBoundResult electricityCostLowerBound;
        private Duration generationDuration;

        public Builder planningRunId(String v)                              { planningRunId = v; return this; }
        public Builder assignmentExplanations(List<AssignmentExplanation> v) { assignmentExplanations = v; return this; }
        public Builder chargingExplanations(List<ChargingExplanation> v)   { chargingExplanations = v; return this; }
        public Builder vehicleTimelines(Map<String, VehicleTimeline> v)    { vehicleTimelines = v; return this; }
        public Builder costBreakdown(CostBreakdown v)                       { costBreakdown = v; return this; }
        public Builder qualityReport(PlanQualityReport v)                   { qualityReport = v; return this; }
        public Builder backupTripLowerBound(LowerBoundResult v)             { backupTripLowerBound = v; return this; }
        public Builder electricityCostLowerBound(LowerBoundResult v)        { electricityCostLowerBound = v; return this; }
        public Builder generationDuration(Duration v)                       { generationDuration = v; return this; }
        public PlanExplanation build()                                       { return new PlanExplanation(this); }
    }
}
