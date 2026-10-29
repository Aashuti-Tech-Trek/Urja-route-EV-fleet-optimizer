package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.charging.ChargingCurve;
import com.moveinsync.evfleet.charging.TwoPartChargingCurve;
import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.EnergyModel;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.travel.DistanceMatrix;
import com.moveinsync.evfleet.travel.TravelTimeEstimator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Schedules charging sessions for electric vehicles at charging hubs.
 *
 * <p>Integrates:
 * <ul>
 *   <li>{@link TwoPartChargingCurve} for fast (&lt;80%) and taper (&gt;80%) charging phases.</li>
 *   <li>{@link com.moveinsync.evfleet.time.TariffSchedule} for dynamic peak/off-peak energy costing.</li>
 *   <li>{@link ChargerOccupancyTracker} for enforcing physical charger plug concurrency limits.</li>
 *   <li>Battery safety reserve floor (15%) throughout repositioning to the hub.</li>
 * </ul>
 */
public final class ChargingScheduler {

    private static final double RESERVE_FLOOR = 0.15;
    private static final double SOC_TOLERANCE = 1e-4;
    private static final AtomicLong SESSION_ID_GEN = new AtomicLong(1000);

    private final EnergyModel energyModel;
    private final EnergyFactors energyFactors;
    private final ChargingCurve chargingCurve;
    private final TravelTimeEstimator travelEstimator;
    private final DistanceMatrix distanceMatrix;

    public ChargingScheduler(EnergyModel energyModel, EnergyFactors energyFactors,
                             ChargingCurve chargingCurve, TravelTimeEstimator travelEstimator,
                             DistanceMatrix distanceMatrix) {
        this.energyModel = Objects.requireNonNull(energyModel, "energyModel must not be null");
        this.energyFactors = Objects.requireNonNull(energyFactors, "energyFactors must not be null");
        this.chargingCurve = Objects.requireNonNull(chargingCurve, "chargingCurve must not be null");
        this.travelEstimator = Objects.requireNonNull(travelEstimator, "travelEstimator must not be null");
        this.distanceMatrix = Objects.requireNonNull(distanceMatrix, "distanceMatrix must not be null");
    }

    /**
     * Attempts to schedule a charging session for a vehicle at a given hub up to targetSoc,
     * ensuring completion on or before windowEnd.
     *
     * @param vehicle the vehicle requiring charging
     * @param timeline the vehicle's current timeline
     * @param hub the target charging hub
     * @param targetSoc desired state of charge (clamped to at most 1.0)
     * @param windowEnd deadline by which charging must be completed (e.g. to depart for next trip)
     * @param occupancyTracker charger occupancy tracker for capacity checks
     * @return ScheduledChargingPlan indicating success or specific failure reason
     */
    public ScheduledChargingPlan planCharging(Vehicle vehicle, VehicleTimeline timeline,
                                             ChargerHub hub, double targetSoc,
                                             Instant windowEnd,
                                             ChargerOccupancyTracker occupancyTracker) {
        Objects.requireNonNull(vehicle, "vehicle must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        Objects.requireNonNull(hub, "hub must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        Objects.requireNonNull(occupancyTracker, "occupancyTracker must not be null");

        double batteryCapacity = vehicle.getBatteryCapacityKwh();
        double currentSoc = timeline.getCurrentSoc();
        Location currentLoc = timeline.getCurrentLocation();
        Instant currentAvailable = timeline.getCurrentAvailableTime();

        // 1. Repositioning / Deadhead drive to Hub
        double deadheadDist = distanceMatrix.getRoadDistanceKm(currentLoc, hub.getLocation());
        Duration deadheadTime = travelEstimator.estimateTravelTime(currentLoc, hub.getLocation());
        Instant arrivalAtHub = currentAvailable.plus(deadheadTime);

        double deadheadEnergy = deadheadDist > 0.0
                ? energyModel.calculateEnergyRequired(deadheadDist, vehicle.getBaseConsumptionKwhPerKm(), energyFactors)
                : 0.0;
        double socDropDeadhead = deadheadEnergy / batteryCapacity;
        double arrivalSoc = currentSoc - socDropDeadhead;

        if (arrivalSoc < (RESERVE_FLOOR - SOC_TOLERANCE)) {
            return ScheduledChargingPlan.infeasible(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE,
                    String.format("Deadhead to hub %s would deplete SoC to %.4f, breaching 15%% reserve floor",
                            hub.getHubId(), arrivalSoc));
        }

        double desiredTargetSoc = Math.min(1.0, Math.max(targetSoc, arrivalSoc));
        if (desiredTargetSoc <= arrivalSoc + 0.001) {
            // Already at or above desired SoC
            return ScheduledChargingPlan.infeasible(FeasibilityReason.NONE, "Vehicle already at desired SoC");
        }

        // 2. Charging duration calculation using TwoPartChargingCurve
        double chargingHours = chargingCurve.calculateChargingTimeHours(
                batteryCapacity, arrivalSoc, desiredTargetSoc, hub.getChargerPowerKw());
        long chargingSeconds = Math.max(1, (long) Math.ceil(chargingHours * 3600.0));
        Duration chargingDuration = Duration.ofSeconds(chargingSeconds);

        Instant sessionStart = arrivalAtHub;
        Instant sessionEnd = sessionStart.plus(chargingDuration);

        if (sessionEnd.isAfter(windowEnd)) {
            return ScheduledChargingPlan.infeasible(FeasibilityReason.DEADLINE_INFEASIBLE,
                    String.format("Charging session would finish at %s, after required departure window %s",
                            sessionEnd, windowEnd));
        }

        // 3. Charger concurrency / capacity check at Hub
        if (!occupancyTracker.hasCapacity(hub.getHubId(), sessionStart, sessionEnd, hub.getChargerCount())) {
            return ScheduledChargingPlan.infeasible(FeasibilityReason.CHARGER_CAPACITY_UNAVAILABLE,
                    String.format("Hub %s has all %d chargers occupied between %s and %s",
                            hub.getHubId(), hub.getChargerCount(), sessionStart, sessionEnd));
        }

        // 4. Energy delivered and Cost calculation
        double energyDelivered = chargingCurve.calculateEnergyDeliveredKwh(batteryCapacity, arrivalSoc, desiredTargetSoc);
        BigDecimal cost = hub.getTariffSchedule().calculateCost(sessionStart, sessionEnd, energyDelivered);

        String sessionId = "CS-" + SESSION_ID_GEN.incrementAndGet();
        ChargingSession session = new ChargingSession(
                sessionId,
                vehicle.getVehicleId(),
                hub.getHubId(),
                sessionStart,
                sessionEnd,
                arrivalSoc,
                desiredTargetSoc,
                energyDelivered,
                cost,
                hub.getTariffSchedule().getCurrency()
        );

        // 5. Build timeline events
        TimelineEvent deadheadEvent = null;
        if (deadheadDist > 0.0) {
            deadheadEvent = TimelineEvent.emptyDrive(
                    currentAvailable,
                    arrivalAtHub,
                    currentLoc,
                    hub.getLocation(),
                    deadheadDist,
                    deadheadEnergy,
                    currentSoc,
                    arrivalSoc,
                    "DEADHEAD-TO-" + hub.getHubId()
            );
        }

        TimelineEvent chargingEvent = TimelineEvent.charging(
                sessionStart,
                sessionEnd,
                hub.getLocation(),
                energyDelivered,
                arrivalSoc,
                desiredTargetSoc,
                sessionId,
                hub.getHubId()
        );

        return ScheduledChargingPlan.feasible(
                session,
                deadheadEvent,
                chargingEvent,
                desiredTargetSoc,
                sessionEnd,
                hub.getLocation()
        );
    }
}
