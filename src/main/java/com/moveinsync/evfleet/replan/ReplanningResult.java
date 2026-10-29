package com.moveinsync.evfleet.replan;

import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.validator.ValidationReport;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of a live replanning operation.
 */
public final class ReplanningResult {

    private final String replanningId;
    private final ReplanningOutcome outcome;
    private final String message;
    private final FleetPlanResult proposedPlan;          // null if INFEASIBLE or STALE
    private final ValidationReport validationReport;      // from independent validator
    private final int preservedAssignmentsCount;
    private final int changedAssignmentsCount;
    private final int changedChargingSessionsCount;
    private final int backupCountBefore;
    private final int backupCountAfter;
    private final BigDecimal costBefore;
    private final BigDecimal costAfter;
    private final Duration elapsedDuration;
    private final boolean timeBudgetExceeded;
    private final List<String> unresolvedTripIds;

    public ReplanningResult(Builder b) {
        this.replanningId = Objects.requireNonNull(b.replanningId, "replanningId");
        this.outcome = Objects.requireNonNull(b.outcome, "outcome");
        this.message = b.message != null ? b.message : "";
        this.proposedPlan = b.proposedPlan;
        this.validationReport = b.validationReport;
        this.preservedAssignmentsCount = b.preservedAssignmentsCount;
        this.changedAssignmentsCount = b.changedAssignmentsCount;
        this.changedChargingSessionsCount = b.changedChargingSessionsCount;
        this.backupCountBefore = b.backupCountBefore;
        this.backupCountAfter = b.backupCountAfter;
        this.costBefore = b.costBefore != null ? b.costBefore : BigDecimal.ZERO;
        this.costAfter = b.costAfter != null ? b.costAfter : BigDecimal.ZERO;
        this.elapsedDuration = b.elapsedDuration != null ? b.elapsedDuration : Duration.ZERO;
        this.timeBudgetExceeded = b.timeBudgetExceeded;
        this.unresolvedTripIds = b.unresolvedTripIds != null ? List.copyOf(b.unresolvedTripIds) : Collections.emptyList();
    }

    public String getReplanningId() { return replanningId; }
    public ReplanningOutcome getOutcome() { return outcome; }
    public String getMessage() { return message; }
    public Optional<FleetPlanResult> getProposedPlan() { return Optional.ofNullable(proposedPlan); }
    public Optional<ValidationReport> getValidationReport() { return Optional.ofNullable(validationReport); }
    public int getPreservedAssignmentsCount() { return preservedAssignmentsCount; }
    public int getChangedAssignmentsCount() { return changedAssignmentsCount; }
    public int getChangedChargingSessionsCount() { return changedChargingSessionsCount; }
    public int getBackupCountBefore() { return backupCountBefore; }
    public int getBackupCountAfter() { return backupCountAfter; }
    public BigDecimal getCostBefore() { return costBefore; }
    public BigDecimal getCostAfter() { return costAfter; }
    public Duration getElapsedDuration() { return elapsedDuration; }
    public boolean isTimeBudgetExceeded() { return timeBudgetExceeded; }
    public List<String> getUnresolvedTripIds() { return unresolvedTripIds; }

    public static final class Builder {
        private String replanningId;
        private ReplanningOutcome outcome;
        private String message;
        private FleetPlanResult proposedPlan;
        private ValidationReport validationReport;
        private int preservedAssignmentsCount;
        private int changedAssignmentsCount;
        private int changedChargingSessionsCount;
        private int backupCountBefore;
        private int backupCountAfter;
        private BigDecimal costBefore;
        private BigDecimal costAfter;
        private Duration elapsedDuration;
        private boolean timeBudgetExceeded;
        private List<String> unresolvedTripIds;

        public Builder replanningId(String v) { this.replanningId = v; return this; }
        public Builder outcome(ReplanningOutcome v) { this.outcome = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder proposedPlan(FleetPlanResult v) { this.proposedPlan = v; return this; }
        public Builder validationReport(ValidationReport v) { this.validationReport = v; return this; }
        public Builder preservedAssignmentsCount(int v) { this.preservedAssignmentsCount = v; return this; }
        public Builder changedAssignmentsCount(int v) { this.changedAssignmentsCount = v; return this; }
        public Builder changedChargingSessionsCount(int v) { this.changedChargingSessionsCount = v; return this; }
        public Builder backupCountBefore(int v) { this.backupCountBefore = v; return this; }
        public Builder backupCountAfter(int v) { this.backupCountAfter = v; return this; }
        public Builder costBefore(BigDecimal v) { this.costBefore = v; return this; }
        public Builder costAfter(BigDecimal v) { this.costAfter = v; return this; }
        public Builder elapsedDuration(Duration v) { this.elapsedDuration = v; return this; }
        public Builder timeBudgetExceeded(boolean v) { this.timeBudgetExceeded = v; return this; }
        public Builder unresolvedTripIds(List<String> v) { this.unresolvedTripIds = v; return this; }

        public ReplanningResult build() {
            return new ReplanningResult(this);
        }
    }
}
