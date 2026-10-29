package com.moveinsync.evfleet.planner;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Machine-readable summary of a completed planning run's quality, costs, and solver metrics.
 */
public final class PlanningQualityReport {

    private final int tripsRequested;
    private final int tripsAssignedToEv;
    private final int tripsAssignedToBackup;
    private final BigDecimal totalChargingCost;
    private final String currency;
    private final int chargingSessionsCount;
    private final Map<String, Integer> sessionsPerHub;
    private final Duration planningRuntime;
    private final int localSearchIterations;
    private final int candidateEvaluations;
    private final boolean validationPassed;
    private final int backupLowerBound;
    private final String lowerBoundAssumptions;

    public PlanningQualityReport(int tripsRequested, int tripsAssignedToEv, int tripsAssignedToBackup,
                                 BigDecimal totalChargingCost, String currency,
                                 int chargingSessionsCount, Map<String, Integer> sessionsPerHub,
                                 Duration planningRuntime, int localSearchIterations,
                                 int candidateEvaluations, boolean validationPassed,
                                 int backupLowerBound, String lowerBoundAssumptions) {
        this.tripsRequested = tripsRequested;
        this.tripsAssignedToEv = tripsAssignedToEv;
        this.tripsAssignedToBackup = tripsAssignedToBackup;
        this.totalChargingCost = Objects.requireNonNull(totalChargingCost, "totalChargingCost");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.chargingSessionsCount = chargingSessionsCount;
        this.sessionsPerHub = sessionsPerHub != null ? Map.copyOf(sessionsPerHub) : Collections.emptyMap();
        this.planningRuntime = Objects.requireNonNull(planningRuntime, "planningRuntime");
        this.localSearchIterations = localSearchIterations;
        this.candidateEvaluations = candidateEvaluations;
        this.validationPassed = validationPassed;
        this.backupLowerBound = backupLowerBound;
        this.lowerBoundAssumptions = lowerBoundAssumptions != null ? lowerBoundAssumptions : "";
    }

    public int getTripsRequested() { return tripsRequested; }
    public int getTripsAssignedToEv() { return tripsAssignedToEv; }
    public int getTripsAssignedToBackup() { return tripsAssignedToBackup; }
    public BigDecimal getTotalChargingCost() { return totalChargingCost; }
    public String getCurrency() { return currency; }
    public int getChargingSessionsCount() { return chargingSessionsCount; }
    public Map<String, Integer> getSessionsPerHub() { return sessionsPerHub; }
    public Duration getPlanningRuntime() { return planningRuntime; }
    public int getLocalSearchIterations() { return localSearchIterations; }
    public int getCandidateEvaluations() { return candidateEvaluations; }
    public boolean isValidationPassed() { return validationPassed; }
    public int getBackupLowerBound() { return backupLowerBound; }
    public String getLowerBoundAssumptions() { return lowerBoundAssumptions; }

    public double getEvCoveragePercentage() {
        return tripsRequested > 0 ? (tripsAssignedToEv * 100.0) / tripsRequested : 100.0;
    }

    @Override
    public String toString() {
        return String.format(
                "PlanningQualityReport[trips=%d (EV=%d, Backup=%d, %.1f%% EV), cost=%s %s, sessions=%d, runtime=%dms, valid=%s]",
                tripsRequested, tripsAssignedToEv, tripsAssignedToBackup, getEvCoveragePercentage(),
                currency, totalChargingCost, chargingSessionsCount, planningRuntime.toMillis(), validationPassed);
    }
}
