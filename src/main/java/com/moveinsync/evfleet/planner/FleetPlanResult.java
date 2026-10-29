package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.ChargingSession;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Authoritative, immutable result of a fleet planning run.
 */
public final class FleetPlanResult {

    public enum PlanStatus {
        SUCCESS,
        PARTIAL_COVERAGE,
        NO_FEASIBLE_ASSIGNMENT
    }

    private final String planningRunId;
    private final PlanStatus status;
    private final List<TripAssignment> tripAssignments;
    private final Map<String, VehicleTimeline> vehicleTimelines;
    private final List<ChargingSession> chargingSessions;
    private final int totalBackupTripCount;
    private final BigDecimal totalChargingCost;
    private final String currency;
    private final Map<String, BigDecimal> costByHub;
    private final Duration executionDuration;
    private final String heuristicVersion;
    private final String validationStatus;
    private final PlanningQualityReport qualityReport;

    public FleetPlanResult(String planningRunId, PlanStatus status,
                           List<TripAssignment> tripAssignments,
                           Map<String, VehicleTimeline> vehicleTimelines,
                           List<ChargingSession> chargingSessions,
                           int totalBackupTripCount, BigDecimal totalChargingCost,
                           String currency, Map<String, BigDecimal> costByHub,
                           Duration executionDuration, String heuristicVersion,
                           String validationStatus, PlanningQualityReport qualityReport) {
        this.planningRunId = Objects.requireNonNull(planningRunId, "planningRunId");
        this.status = Objects.requireNonNull(status, "status");
        this.tripAssignments = List.copyOf(Objects.requireNonNull(tripAssignments, "tripAssignments"));
        this.vehicleTimelines = Map.copyOf(Objects.requireNonNull(vehicleTimelines, "vehicleTimelines"));
        this.chargingSessions = List.copyOf(Objects.requireNonNull(chargingSessions, "chargingSessions"));
        this.totalBackupTripCount = totalBackupTripCount;
        this.totalChargingCost = Objects.requireNonNull(totalChargingCost, "totalChargingCost");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.costByHub = costByHub != null ? Map.copyOf(costByHub) : Collections.emptyMap();
        this.executionDuration = Objects.requireNonNull(executionDuration, "executionDuration");
        this.heuristicVersion = Objects.requireNonNull(heuristicVersion, "heuristicVersion");
        this.validationStatus = Objects.requireNonNull(validationStatus, "validationStatus");
        this.qualityReport = qualityReport;
    }

    public String getPlanningRunId() { return planningRunId; }
    public PlanStatus getStatus() { return status; }
    public List<TripAssignment> getTripAssignments() { return tripAssignments; }
    public Map<String, VehicleTimeline> getVehicleTimelines() { return vehicleTimelines; }
    public List<ChargingSession> getChargingSessions() { return chargingSessions; }
    public int getTotalBackupTripCount() { return totalBackupTripCount; }
    public BigDecimal getTotalChargingCost() { return totalChargingCost; }
    public String getCurrency() { return currency; }
    public Map<String, BigDecimal> getCostByHub() { return costByHub; }
    public Duration getExecutionDuration() { return executionDuration; }
    public String getHeuristicVersion() { return heuristicVersion; }
    public String getValidationStatus() { return validationStatus; }
    public PlanningQualityReport getQualityReport() { return qualityReport; }

    public List<TripAssignment> getEvAssignments() {
        return tripAssignments.stream().filter(TripAssignment::isEvAssignment).collect(Collectors.toList());
    }

    public List<TripAssignment> getBackupAssignments() {
        return tripAssignments.stream().filter(TripAssignment::isBackupAssignment).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("FleetPlanResult[runId=%s, status=%s, total=%d (EV=%d, Backup=%d), cost=%s %s, valid=%s]",
                planningRunId, status, tripAssignments.size(),
                getEvAssignments().size(), totalBackupTripCount, currency, totalChargingCost, validationStatus);
    }
}
