package com.moveinsync.evfleet.explain;

import com.moveinsync.evfleet.validator.ValidationReport;
import com.moveinsync.evfleet.validator.ValidationStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Plan quality metrics derived from the plan, input, and independent validation.
 *
 * <p>Every metric declares its derivation source. Unavailable metrics carry explicit reasons.
 * Division by zero is guarded at every percentage/average computation.
 */
public final class PlanQualityReport {

    // --- Assignment metrics (directly from plan) ---
    private final int totalTripsRequested;
    private final int evAssignedTrips;
    private final int backupAssignedTrips;
    private final int vehiclesUsed;
    private final int chargingSessionsCount;

    // --- Energy and charging metrics ---
    private final double totalEnergyConsumedKwh;  // from VehicleTimeline events
    private final double totalEnergyChargedKwh;   // from ChargingSessions
    private final BigDecimal totalElectricityCost;
    private final String currency;
    private final int peakSimultaneousChargingSessions; // from sweep over session intervals
    private final OptionalDouble minimumObservedSoc;    // minimum across all timeline events
    private final OptionalDouble minimumReserveMargin;  // = minimumSoc - 0.15 reserve floor

    // --- Planning algorithm metrics (from PlanningQualityReport) ---
    private final Duration plannerRuntime;
    private final int candidateEvaluations;
    private final int localSearchIterations;
    private final boolean plannerBudgetExhausted; // always false for current greedy
    private final boolean validated;
    private final ValidationStatus validationStatus;
    private final int violationCount;
    private final String heuristicLabel; // "HEURISTIC" — not proven optimal

    private PlanQualityReport(Builder b) {
        this.totalTripsRequested            = b.totalTripsRequested;
        this.evAssignedTrips                = b.evAssignedTrips;
        this.backupAssignedTrips            = b.backupAssignedTrips;
        this.vehiclesUsed                   = b.vehiclesUsed;
        this.chargingSessionsCount          = b.chargingSessionsCount;
        this.totalEnergyConsumedKwh         = b.totalEnergyConsumedKwh;
        this.totalEnergyChargedKwh          = b.totalEnergyChargedKwh;
        this.totalElectricityCost           = Objects.requireNonNull(b.totalElectricityCost, "totalElectricityCost");
        this.currency                       = Objects.requireNonNull(b.currency, "currency");
        this.peakSimultaneousChargingSessions = b.peakSimultaneousChargingSessions;
        this.minimumObservedSoc             = b.minimumObservedSoc;
        this.minimumReserveMargin           = b.minimumReserveMargin;
        this.plannerRuntime                 = b.plannerRuntime != null ? b.plannerRuntime : Duration.ZERO;
        this.candidateEvaluations           = b.candidateEvaluations;
        this.localSearchIterations          = b.localSearchIterations;
        this.plannerBudgetExhausted         = b.plannerBudgetExhausted;
        this.validated                      = b.validated;
        this.validationStatus               = b.validationStatus != null ? b.validationStatus : ValidationStatus.INDETERMINATE;
        this.violationCount                 = b.violationCount;
        this.heuristicLabel                 = "HEURISTIC — not proven globally optimal";
    }

    // --- Derived metrics ---
    public double getEvCoveragePercentage() {
        return totalTripsRequested > 0 ? (evAssignedTrips * 100.0) / totalTripsRequested : 100.0;
    }

    // --- Getters ---
    public int getTotalTripsRequested()           { return totalTripsRequested; }
    public int getEvAssignedTrips()               { return evAssignedTrips; }
    public int getBackupAssignedTrips()           { return backupAssignedTrips; }
    public int getVehiclesUsed()                  { return vehiclesUsed; }
    public int getChargingSessionsCount()         { return chargingSessionsCount; }
    public double getTotalEnergyConsumedKwh()     { return totalEnergyConsumedKwh; }
    public double getTotalEnergyChargedKwh()      { return totalEnergyChargedKwh; }
    public BigDecimal getTotalElectricityCost()   { return totalElectricityCost; }
    public String getCurrency()                   { return currency; }
    public int getPeakSimultaneousChargingSessions() { return peakSimultaneousChargingSessions; }
    public OptionalDouble getMinimumObservedSoc() { return minimumObservedSoc; }
    public OptionalDouble getMinimumReserveMargin() { return minimumReserveMargin; }
    public Duration getPlannerRuntime()           { return plannerRuntime; }
    public int getCandidateEvaluations()          { return candidateEvaluations; }
    public int getLocalSearchIterations()         { return localSearchIterations; }
    public boolean isPlannerBudgetExhausted()     { return plannerBudgetExhausted; }
    public boolean isValidated()                  { return validated; }
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public int getViolationCount()                { return violationCount; }
    public String getHeuristicLabel()             { return heuristicLabel; }

    @Override
    public String toString() {
        return String.format(
                "PlanQualityReport[trips=%d (EV=%d %.1f%%, backup=%d), sessions=%d, cost=%s %s, " +
                "minSoC=%.2f%%, valid=%s/%d violations, runtime=%dms]",
                totalTripsRequested, evAssignedTrips, getEvCoveragePercentage(), backupAssignedTrips,
                chargingSessionsCount, currency, totalElectricityCost,
                minimumObservedSoc.orElse(Double.NaN) * 100,
                validationStatus, violationCount, plannerRuntime.toMillis());
    }

    public static final class Builder {
        private int totalTripsRequested;
        private int evAssignedTrips;
        private int backupAssignedTrips;
        private int vehiclesUsed;
        private int chargingSessionsCount;
        private double totalEnergyConsumedKwh;
        private double totalEnergyChargedKwh;
        private BigDecimal totalElectricityCost = BigDecimal.ZERO;
        private String currency = "INR";
        private int peakSimultaneousChargingSessions;
        private OptionalDouble minimumObservedSoc = OptionalDouble.empty();
        private OptionalDouble minimumReserveMargin = OptionalDouble.empty();
        private Duration plannerRuntime;
        private int candidateEvaluations;
        private int localSearchIterations;
        private boolean plannerBudgetExhausted;
        private boolean validated;
        private ValidationStatus validationStatus;
        private int violationCount;

        public Builder totalTripsRequested(int v)           { totalTripsRequested = v; return this; }
        public Builder evAssignedTrips(int v)               { evAssignedTrips = v; return this; }
        public Builder backupAssignedTrips(int v)           { backupAssignedTrips = v; return this; }
        public Builder vehiclesUsed(int v)                  { vehiclesUsed = v; return this; }
        public Builder chargingSessionsCount(int v)         { chargingSessionsCount = v; return this; }
        public Builder totalEnergyConsumedKwh(double v)     { totalEnergyConsumedKwh = v; return this; }
        public Builder totalEnergyChargedKwh(double v)      { totalEnergyChargedKwh = v; return this; }
        public Builder totalElectricityCost(BigDecimal v)   { totalElectricityCost = v; return this; }
        public Builder currency(String v)                   { currency = v; return this; }
        public Builder peakSimultaneousChargingSessions(int v) { peakSimultaneousChargingSessions = v; return this; }
        public Builder minimumObservedSoc(OptionalDouble v) { minimumObservedSoc = v; return this; }
        public Builder minimumReserveMargin(OptionalDouble v) { minimumReserveMargin = v; return this; }
        public Builder plannerRuntime(Duration v)           { plannerRuntime = v; return this; }
        public Builder candidateEvaluations(int v)          { candidateEvaluations = v; return this; }
        public Builder localSearchIterations(int v)         { localSearchIterations = v; return this; }
        public Builder plannerBudgetExhausted(boolean v)   { plannerBudgetExhausted = v; return this; }
        public Builder validated(boolean v)                 { validated = v; return this; }
        public Builder validationStatus(ValidationStatus v) { validationStatus = v; return this; }
        public Builder violationCount(int v)                { violationCount = v; return this; }
        public PlanQualityReport build()                    { return new PlanQualityReport(this); }
    }
}
