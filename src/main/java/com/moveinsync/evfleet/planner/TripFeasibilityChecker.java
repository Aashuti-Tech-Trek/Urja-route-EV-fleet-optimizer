package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.domain.VehicleStatus;
import com.moveinsync.evfleet.energy.BatterySafetyCalculator;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.EnergyModel;
import com.moveinsync.evfleet.travel.DistanceMatrix;
import com.moveinsync.evfleet.travel.TravelTimeEstimator;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.Duration;
import java.time.Instant;

/**
 * Evaluates whether a vehicle can feasibly serve a trip from its current planning state,
 * respecting all hard constraints.
 *
 * <p><strong>Evaluation order (first failing check wins):</strong>
 * <ol>
 *   <li>Vehicle availability (status and availableFrom time).</li>
 *   <li>Arrival timing at pickup: can the vehicle reach pickup before scheduled start time?</li>
 *   <li>Trip deadline: can the trip finish by latestAllowedFinishTime?</li>
 *   <li>Energy for empty drive: does battery stay above 15% reserve after driving to pickup?</li>
 *   <li>Energy for trip: does battery stay above 15% reserve after the assigned trip?</li>
 * </ol>
 *
 * <p><strong>Energy model:</strong> Uses the supplied {@link EnergyModel} with the supplied
 * {@link EnergyFactors}. Trip distance is taken from {@link Trip#getDistanceKm()} (the declared
 * trip distance), which is the source of truth for the assigned trip. The empty-drive distance
 * is computed from {@link DistanceMatrix} using the {@link TravelTimeEstimator}'s road-distance
 * approximation.
 *
 * <p><strong>Limitations:</strong>
 * <ul>
 *   <li>Does not schedule or check charging within this check. A separate charging-scheduler
 *       component handles that in later phases.</li>
 *   <li>Does not check charger capacity. {@link FeasibilityReason#CHARGER_CAPACITY_UNAVAILABLE}
 *       is reserved for use by the charging scheduler.</li>
 *   <li>Does not account for deadhead travel time variability (uses the default estimator).</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe given an immutable {@link DistanceMatrix} per
 * planning run. The {@link DistanceMatrix} itself may be shared across threads (it uses a
 * ConcurrentHashMap internally).
 */
public final class TripFeasibilityChecker {

    private final EnergyModel energyModel;
    private final EnergyFactors energyFactors;
    private final TravelTimeEstimator travelTimeEstimator;
    private final DistanceMatrix distanceMatrix;

    /**
     * Constructs a TripFeasibilityChecker with all required dependencies.
     *
     * @param energyModel          energy consumption model; must not be null
     * @param energyFactors        traffic/AC factors for energy calculation; must not be null
     * @param travelTimeEstimator  estimator for travel time; must not be null
     * @param distanceMatrix       shared distance cache for the planning run; must not be null
     */
    public TripFeasibilityChecker(EnergyModel energyModel, EnergyFactors energyFactors,
                                   TravelTimeEstimator travelTimeEstimator,
                                   DistanceMatrix distanceMatrix) {
        this.energyModel = DomainValidator.requireNonNull(energyModel, "Energy model");
        this.energyFactors = DomainValidator.requireNonNull(energyFactors, "Energy factors");
        this.travelTimeEstimator = DomainValidator.requireNonNull(travelTimeEstimator, "Travel time estimator");
        this.distanceMatrix = DomainValidator.requireNonNull(distanceMatrix, "Distance matrix");
    }

    /**
     * Evaluates whether {@code vehicle} can serve {@code trip} from its current planning state.
     *
     * @param vehicle the vehicle to evaluate; must not be null
     * @param trip    the trip to check; must not be null
     * @return a structured {@link FeasibilityResult}; never null
     */
    public FeasibilityResult check(Vehicle vehicle, Trip trip) {
        if (vehicle == null) {
            return FeasibilityResult.infeasible(FeasibilityReason.INVALID_INPUT,
                    "Vehicle must not be null");
        }
        if (trip == null) {
            return FeasibilityResult.infeasible(FeasibilityReason.INVALID_INPUT,
                    "Trip must not be null");
        }

        // ── 1. Vehicle availability ──────────────────────────────────────────────
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            return FeasibilityResult.infeasible(FeasibilityReason.VEHICLE_UNAVAILABLE,
                    String.format("Vehicle %s is not AVAILABLE (current status: %s)",
                            vehicle.getVehicleId(), vehicle.getStatus()));
        }

        // ── 2. Compute empty-drive time and arrival time ─────────────────────────
        Duration emptyDriveTime = travelTimeEstimator.estimateTravelTime(
                vehicle.getCurrentLocation(), trip.getStartLocation());

        Instant vehicleAvailableFrom = vehicle.getAvailableFrom();
        Instant estimatedArrivalAtPickup = vehicleAvailableFrom.plus(emptyDriveTime);
        Instant tripStart = trip.getScheduledStartTime();

        if (estimatedArrivalAtPickup.isAfter(tripStart)) {
            return FeasibilityResult.infeasible(FeasibilityReason.PICKUP_UNREACHABLE,
                    String.format(
                            "Vehicle %s estimated arrival at pickup is %s, but trip scheduled start is %s "
                            + "(vehicle available from %s, empty drive %s). "
                            + "Vehicle arrives %s late.",
                            vehicle.getVehicleId(), estimatedArrivalAtPickup, tripStart,
                            vehicleAvailableFrom, emptyDriveTime,
                            Duration.between(tripStart, estimatedArrivalAtPickup)));
        }

        // ── 3. Trip deadline feasibility ─────────────────────────────────────────
        Duration tripTravelTime = travelTimeEstimator.estimateTravelTime(
                trip.getStartLocation(), trip.getDestination());
        Instant estimatedTripFinish = tripStart.plus(tripTravelTime);
        Instant latestFinish = trip.getLatestAllowedFinishTime();

        if (estimatedTripFinish.isAfter(latestFinish)) {
            return FeasibilityResult.infeasible(FeasibilityReason.DEADLINE_INFEASIBLE,
                    String.format(
                            "Trip %s estimated finish is %s, which exceeds latestAllowedFinishTime %s "
                            + "(trip start %s, travel time %s, overrun by %s).",
                            trip.getTripId(), estimatedTripFinish, latestFinish,
                            tripStart, tripTravelTime,
                            Duration.between(latestFinish, estimatedTripFinish)));
        }

        // ── 4. Energy for empty drive ────────────────────────────────────────────
        double emptyDriveDistanceKm = distanceMatrix.getRoadDistanceKm(
                vehicle.getCurrentLocation(), trip.getStartLocation());

        double emptyDriveEnergyKwh = energyModel.calculateEnergyRequired(
                emptyDriveDistanceKm,
                vehicle.getBaseConsumptionKwhPerKm(),
                energyFactors);

        double batteryCapacity = vehicle.getBatteryCapacityKwh();
        double socBeforeEmptyDrive = vehicle.getStateOfCharge();

        if (emptyDriveDistanceKm > 0.0 && !BatterySafetyCalculator.canSustainTrip(batteryCapacity, socBeforeEmptyDrive, emptyDriveEnergyKwh)) {
            double availableEnergy = BatterySafetyCalculator.calculateAvailableEnergyAboveReserve(
                    batteryCapacity, socBeforeEmptyDrive);
            return FeasibilityResult.infeasible(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE,
                    String.format(
                            "Vehicle %s cannot complete empty drive to pickup (%.2f km, requires %.3f kWh) "
                            + "while maintaining 15%% reserve. Available above reserve: %.3f kWh. SoC: %.1f%%.",
                            vehicle.getVehicleId(), emptyDriveDistanceKm, emptyDriveEnergyKwh,
                            availableEnergy, socBeforeEmptyDrive * 100.0));
        }

        double currentEnergyAfterEmptyDrive = BatterySafetyCalculator.calculateCurrentEnergy(
                batteryCapacity, socBeforeEmptyDrive) - emptyDriveEnergyKwh;
        double socAfterEmptyDrive = currentEnergyAfterEmptyDrive / batteryCapacity;

        // ── 5. Energy for the assigned trip ──────────────────────────────────────
        double tripEnergyKwh = energyModel.calculateEnergyRequired(
                trip.getDistanceKm(),
                vehicle.getBaseConsumptionKwhPerKm(),
                energyFactors);

        if (!BatterySafetyCalculator.canSustainTrip(batteryCapacity, socAfterEmptyDrive, tripEnergyKwh)) {
            double availableAfterEmpty = BatterySafetyCalculator.calculateAvailableEnergyAboveReserve(
                    batteryCapacity, socAfterEmptyDrive);
            return FeasibilityResult.infeasible(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP,
                    String.format(
                            "Vehicle %s cannot complete trip %s (%.2f km, requires %.3f kWh) "
                            + "while maintaining 15%% reserve after empty drive. "
                            + "Available above reserve at pickup: %.3f kWh. SoC at pickup: %.1f%%.",
                            vehicle.getVehicleId(), trip.getTripId(),
                            trip.getDistanceKm(), tripEnergyKwh,
                            availableAfterEmpty, socAfterEmptyDrive * 100.0));
        }

        double currentEnergyAfterTrip = currentEnergyAfterEmptyDrive - tripEnergyKwh;
        double socAfterTrip = currentEnergyAfterTrip / batteryCapacity;
        Duration deadlineSlack = Duration.between(estimatedTripFinish, latestFinish);

        return FeasibilityResult.feasible(
                emptyDriveDistanceKm, emptyDriveEnergyKwh, tripEnergyKwh,
                socAfterEmptyDrive, socAfterTrip,
                emptyDriveTime, deadlineSlack);
    }
}
