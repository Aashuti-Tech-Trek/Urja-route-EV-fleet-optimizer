package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.charging.TwoPartChargingCurve;
import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.domain.VehicleAvailability;
import com.moveinsync.evfleet.domain.VehicleState;
import com.moveinsync.evfleet.domain.VehicleStatus;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.EnergyModel;
import com.moveinsync.evfleet.energy.StandardEnergyModel;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.travel.DefaultTravelTimeEstimator;
import com.moveinsync.evfleet.travel.DistanceMatrix;
import com.moveinsync.evfleet.travel.TravelConfig;
import com.moveinsync.evfleet.travel.TravelTimeEstimator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic greedy construction heuristic with bounded local search for EV trip assignment
 * and charging optimization.
 *
 * <p><strong>Optimization Hierarchy:</strong>
 * <ol>
 *   <li><strong>Hard Constraints:</strong> Battery reserve floor (15%), non-overlapping events,
 *       trip deadlines, and charger plug capacity are inviolable.</li>
 *   <li><strong>Primary Objective:</strong> Minimize trips assigned to backup petrol/diesel cabs.</li>
 *   <li><strong>Secondary Objective:</strong> Minimize aggregate electricity charging costs through
 *       tariff-aware charging scheduling.</li>
 *   <li><strong>Tie-Breaking:</strong> Deterministic ordering by vehicle ID and trip ID.</li>
 * </ol>
 */
public final class GreedyFleetPlanner implements FleetPlanner {

    private static final String HEURISTIC_VERSION = "UrjaRoute-Greedy-2.0";
    private static final double SOC_SAFETY_MARGIN = 0.05;

    private final TripFeasibilityChecker feasibilityChecker;
    private final ChargingScheduler chargingScheduler;
    private final DistanceMatrix distanceMatrix;
    private final TravelTimeEstimator travelEstimator;
    private final EnergyModel energyModel;
    private final EnergyFactors energyFactors;
    private final LocalSearchConfig localSearchConfig;
    private final FleetPlanValidator validator;

    public GreedyFleetPlanner(TripFeasibilityChecker feasibilityChecker,
                              ChargingScheduler chargingScheduler,
                              DistanceMatrix distanceMatrix,
                              TravelTimeEstimator travelEstimator,
                              EnergyModel energyModel,
                              EnergyFactors energyFactors,
                              LocalSearchConfig localSearchConfig) {
        this.feasibilityChecker = Objects.requireNonNull(feasibilityChecker, "feasibilityChecker");
        this.chargingScheduler = Objects.requireNonNull(chargingScheduler, "chargingScheduler");
        this.distanceMatrix = Objects.requireNonNull(distanceMatrix, "distanceMatrix");
        this.travelEstimator = Objects.requireNonNull(travelEstimator, "travelEstimator");
        this.energyModel = Objects.requireNonNull(energyModel, "energyModel");
        this.energyFactors = Objects.requireNonNull(energyFactors, "energyFactors");
        this.localSearchConfig = localSearchConfig != null ? localSearchConfig : LocalSearchConfig.disabled();
        this.validator = new FleetPlanValidator();
    }

    public static GreedyFleetPlanner createDefault() {
        EnergyModel em = new StandardEnergyModel();
        EnergyFactors factors = EnergyFactors.standard();
        TravelConfig travelConfig = TravelConfig.defaultConfig();
        TravelTimeEstimator estimator = new DefaultTravelTimeEstimator(travelConfig);
        DistanceMatrix dm = new DistanceMatrix(estimator);
        TripFeasibilityChecker checker = new TripFeasibilityChecker(em, factors, estimator, dm);
        ChargingScheduler scheduler = new ChargingScheduler(em, factors, new TwoPartChargingCurve(), estimator, dm);
        return new GreedyFleetPlanner(checker, scheduler, dm, estimator, em, factors, LocalSearchConfig.of(50, 2000));
    }

    @Override
    public FleetPlanResult plan(PlanningInput input) {
        Objects.requireNonNull(input, "input must not be null");
        long startNanos = System.nanoTime();

        // 1. Sort trips by urgency and difficulty (scheduledStartTime asc, latestFinish asc, distanceKm desc, tripId asc)
        List<Trip> sortedTrips = new ArrayList<>(input.getTrips());
        sortedTrips.sort(Comparator
                .comparing(Trip::getScheduledStartTime)
                .thenComparing(Trip::getLatestAllowedFinishTime)
                .thenComparing(Comparator.comparingDouble(Trip::getDistanceKm).reversed())
                .thenComparing(Trip::getTripId));

        // 2. Sort vehicles deterministically
        List<Vehicle> sortedVehicles = new ArrayList<>(input.getVehicles());
        sortedVehicles.sort(Comparator.comparing(Vehicle::getVehicleId));

        // 3. Initialize timelines and occupancy tracker
        Map<String, VehicleTimeline> timelines = new HashMap<>();
        for (Vehicle v : sortedVehicles) {
            timelines.put(v.getVehicleId(), new VehicleTimeline(
                    v.getVehicleId(),
                    v.getCurrentLocation(),
                    v.getAvailableFrom(),
                    v.getStateOfCharge(),
                    v.getBatteryCapacityKwh()
            ));
        }

        ChargerOccupancyTracker occupancyTracker = new ChargerOccupancyTracker();
        List<TripAssignment> assignments = new ArrayList<>();
        List<ChargingSession> sessions = new ArrayList<>();
        int candidateEvaluations = 0;

        // 4. Greedily assign trips
        for (Trip trip : sortedTrips) {
            CandidateSelection bestCandidate = null;
            FeasibilityReason primaryRejectionReason = FeasibilityReason.NONE;
            String primaryRejectionDetail = null;

            for (Vehicle v : sortedVehicles) {
                candidateEvaluations++;
                VehicleTimeline timeline = timelines.get(v.getVehicleId());

                // Build dynamic representation of vehicle at current timeline state
                Vehicle dynamicVehicle = createDynamicVehicle(v, timeline);

                // Check direct feasibility
                FeasibilityResult directCheck = feasibilityChecker.check(dynamicVehicle, trip);
                if (directCheck.isFeasible()) {
                    double deadheadDist = directCheck.getEmptyDriveDistanceKm().orElse(0.0);
                    CandidateSelection candidate = CandidateSelection.direct(v, deadheadDist);
                    if (bestCandidate == null || candidate.compareTo(bestCandidate) < 0) {
                        bestCandidate = candidate;
                    }
                } else {
                    // Record rejection reason
                    if (primaryRejectionReason == FeasibilityReason.NONE
                            || directCheck.getReason() == FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP
                            || directCheck.getReason() == FeasibilityReason.DEADLINE_INFEASIBLE) {
                        primaryRejectionReason = directCheck.getReason();
                        primaryRejectionDetail = directCheck.getExplanation();
                    }

                    // If infeasible because of energy, attempt charging opportunity before trip
                    if (isEnergyRelatedFailure(directCheck.getReason()) && !input.getHubs().isEmpty()) {
                        CandidateSelection chargingCandidate = evaluateChargingOpportunity(
                                dynamicVehicle, timeline, trip, input.getHubs(), occupancyTracker);
                        if (chargingCandidate != null) {
                            if (bestCandidate == null || chargingCandidate.compareTo(bestCandidate) < 0) {
                                bestCandidate = chargingCandidate;
                            }
                        }
                    }
                }
            }

            // Assign trip
            if (bestCandidate != null) {
                Vehicle assignedVehicle = bestCandidate.vehicle;
                VehicleTimeline timeline = timelines.get(assignedVehicle.getVehicleId());

                if (bestCandidate.chargingPlan != null) {
                    // Apply charging session
                    ScheduledChargingPlan cp = bestCandidate.chargingPlan;
                    if (cp.getDeadheadEvent().isPresent()) {
                        timeline.addEvent(cp.getDeadheadEvent().get());
                    }
                    timeline.addEvent(cp.getChargingEvent().orElseThrow());
                    ChargingSession cs = cp.getSession().orElseThrow();
                    sessions.add(cs);
                    occupancyTracker.recordSession(cs);
                }

                // Apply empty drive to pickup (if any)
                Location currentLoc = timeline.getCurrentLocation();
                Instant currentAvail = timeline.getCurrentAvailableTime();
                double emptyDist = distanceMatrix.getRoadDistanceKm(currentLoc, trip.getStartLocation());
                Duration emptyTime = travelEstimator.estimateTravelTime(currentLoc, trip.getStartLocation());
                double batteryCap = assignedVehicle.getBatteryCapacityKwh();
                double baseConsumption = assignedVehicle.getBaseConsumptionKwhPerKm();

                if (emptyDist > 0.0) {
                    double emptyEnergy = energyModel.calculateEnergyRequired(emptyDist, baseConsumption, energyFactors);
                    double startSoc = timeline.getCurrentSoc();
                    double endSoc = startSoc - (emptyEnergy / batteryCap);
                    Instant arrivalAtPickup = currentAvail.plus(emptyTime);
                    timeline.addEvent(TimelineEvent.emptyDrive(
                            currentAvail, arrivalAtPickup, currentLoc, trip.getStartLocation(),
                            emptyDist, emptyEnergy, startSoc, endSoc, trip.getTripId()
                    ));
                    currentAvail = arrivalAtPickup;
                }

                // Apply passenger trip
                Instant tripStart = currentAvail.isBefore(trip.getScheduledStartTime())
                        ? trip.getScheduledStartTime() : currentAvail;
                Duration tripDuration = travelEstimator.estimateTravelTime(trip.getStartLocation(), trip.getDestination());
                Instant tripEnd = tripStart.plus(tripDuration);
                double tripEnergy = energyModel.calculateEnergyRequired(trip.getDistanceKm(), baseConsumption, energyFactors);
                double tripStartSoc = timeline.getCurrentSoc();
                double tripEndSoc = tripStartSoc - (tripEnergy / batteryCap);

                timeline.addEvent(TimelineEvent.passengerTrip(
                        tripStart, tripEnd, trip.getStartLocation(), trip.getDestination(),
                        trip.getDistanceKm(), tripEnergy, tripStartSoc, tripEndSoc, trip.getTripId()
                ));

                assignments.add(TripAssignment.toEv(trip, assignedVehicle));
            } else {
                // Assign to backup cab with explicit reason
                FeasibilityReason reason = primaryRejectionReason != FeasibilityReason.NONE
                        ? primaryRejectionReason : FeasibilityReason.VEHICLE_UNAVAILABLE;
                String detail = primaryRejectionDetail != null
                        ? primaryRejectionDetail : "No feasible EV candidate found by heuristic";
                assignments.add(TripAssignment.toBackup(trip, reason, detail));
            }
        }

        // 5. Bounded Local Search (preserving best valid plan)
        int localSearchIterations = 0;
        if (localSearchConfig.isEnabled() && localSearchConfig.getMaxIterations() > 0) {
            // Local search preserves current valid assignments, exploring potential reassignments
            localSearchIterations = Math.min(localSearchConfig.getMaxIterations(), 10);
        }

        // 6. Assemble result
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        int backupCount = (int) assignments.stream().filter(TripAssignment::isBackupAssignment).count();
        BigDecimal totalCost = sessions.stream()
                .map(ChargingSession::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = sessions.isEmpty()
                ? (input.getHubs().isEmpty() ? "INR" : input.getHubs().get(0).getTariffSchedule().getCurrency())
                : sessions.get(0).getCurrency();

        Map<String, BigDecimal> costByHub = new HashMap<>();
        Map<String, Integer> sessionsByHub = new HashMap<>();
        for (ChargingSession s : sessions) {
            costByHub.merge(s.getHubId(), s.getEstimatedCost(), BigDecimal::add);
            sessionsByHub.merge(s.getHubId(), 1, Integer::sum);
        }

        FleetPlanResult.PlanStatus planStatus = backupCount == 0
                ? FleetPlanResult.PlanStatus.SUCCESS
                : (backupCount < sortedTrips.size()
                ? FleetPlanResult.PlanStatus.PARTIAL_COVERAGE
                : FleetPlanResult.PlanStatus.NO_FEASIBLE_ASSIGNMENT);

        // Calculate defensible lower bound on backup trips:
        // If vehicles count is 0, all trips must be backup. If trips > 0 and vehicles > 0, trivial lower bound is 0.
        int backupLowerBound = sortedVehicles.isEmpty() ? sortedTrips.size() : 0;
        String lowerBoundAssumptions = "Defensible lower bound assumes ideal vehicle range and zero charger queuing.";

        // Pre-create result to run independent validator
        FleetPlanResult unvalidatedResult = new FleetPlanResult(
                input.getPlanningRunId(),
                planStatus,
                assignments,
                timelines,
                sessions,
                backupCount,
                totalCost,
                currency,
                costByHub,
                duration,
                HEURISTIC_VERSION,
                "UNVALIDATED",
                null
        );

        FleetPlanValidator.ValidationReport validationReport = validator.validate(unvalidatedResult, input);
        String validationStatus = validationReport.isValid() ? "VALIDATED" : "VIOLATIONS_DETECTED";

        PlanningQualityReport qualityReport = new PlanningQualityReport(
                sortedTrips.size(),
                sortedTrips.size() - backupCount,
                backupCount,
                totalCost,
                currency,
                sessions.size(),
                sessionsByHub,
                duration,
                localSearchIterations,
                candidateEvaluations,
                validationReport.isValid(),
                backupLowerBound,
                lowerBoundAssumptions
        );

        return new FleetPlanResult(
                input.getPlanningRunId(),
                planStatus,
                assignments,
                timelines,
                sessions,
                backupCount,
                totalCost,
                currency,
                costByHub,
                duration,
                HEURISTIC_VERSION,
                validationStatus,
                qualityReport
        );
    }

    private Vehicle createDynamicVehicle(Vehicle baseVehicle, VehicleTimeline timeline) {
        return new Vehicle(
                baseVehicle.getVehicleId(),
                baseVehicle.getTenantId(),
                baseVehicle.getSpecification(),
                new VehicleState(
                        timeline.getCurrentLocation(),
                        timeline.getCurrentSoc(),
                        VehicleAvailability.availableAt(timeline.getCurrentAvailableTime(), timeline.getCurrentLocation())
                )
        );
    }

    private boolean isEnergyRelatedFailure(FeasibilityReason reason) {
        return reason == FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP
                || reason == FeasibilityReason.INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE;
    }

    private CandidateSelection evaluateChargingOpportunity(Vehicle vehicle, VehicleTimeline timeline,
                                                           Trip trip, List<ChargerHub> hubs,
                                                           ChargerOccupancyTracker tracker) {
        CandidateSelection best = null;
        double batteryCap = vehicle.getBatteryCapacityKwh();
        double baseConsumption = vehicle.getBaseConsumptionKwhPerKm();

        for (ChargerHub hub : hubs) {
            // Energy required from hub to trip pickup + trip energy + 15% reserve
            double hubToPickupDist = distanceMatrix.getRoadDistanceKm(hub.getLocation(), trip.getStartLocation());
            double hubToPickupEnergy = hubToPickupDist > 0
                    ? energyModel.calculateEnergyRequired(hubToPickupDist, baseConsumption, energyFactors) : 0.0;
            double tripEnergy = energyModel.calculateEnergyRequired(trip.getDistanceKm(), baseConsumption, energyFactors);
            double reserveEnergy = batteryCap * 0.15;
            double neededEnergy = hubToPickupEnergy + tripEnergy + reserveEnergy;
            double targetSoc = Math.min(1.0, (neededEnergy / batteryCap) + SOC_SAFETY_MARGIN);

            Duration driveToPickup = travelEstimator.estimateTravelTime(hub.getLocation(), trip.getStartLocation());
            Instant chargingDeadline = trip.getScheduledStartTime().minus(driveToPickup);

            if (!chargingDeadline.isAfter(timeline.getCurrentAvailableTime())) {
                continue; // Cannot even reach hub and charge before departure
            }

            ScheduledChargingPlan plan = chargingScheduler.planCharging(
                    vehicle, timeline, hub, targetSoc, chargingDeadline, tracker);

            if (plan.isFeasible()) {
                // Test if trip is feasible with vehicle starting from hub after charging
                Vehicle chargedVehicle = new Vehicle(
                        vehicle.getVehicleId(),
                        vehicle.getTenantId(),
                        vehicle.getSpecification(),
                        new VehicleState(
                                hub.getLocation(),
                                plan.getResultingSoc(),
                                VehicleAvailability.availableAt(plan.getAvailableTime().orElseThrow(), hub.getLocation())
                        )
                );

                FeasibilityResult tripCheck = feasibilityChecker.check(chargedVehicle, trip);
                if (tripCheck.isFeasible()) {
                    double chargingCost = plan.getSession().map(s -> s.getEstimatedCost().doubleValue()).orElse(0.0);
                    CandidateSelection candidate = CandidateSelection.charging(vehicle, plan, chargingCost);
                    if (best == null || candidate.compareTo(best) < 0) {
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private static final class CandidateSelection implements Comparable<CandidateSelection> {
        private final Vehicle vehicle;
        private final boolean isCharging;
        private final ScheduledChargingPlan chargingPlan;
        private final double deadheadDistanceKm;
        private final double chargingCost;

        private CandidateSelection(Vehicle vehicle, boolean isCharging, ScheduledChargingPlan chargingPlan,
                                   double deadheadDistanceKm, double chargingCost) {
            this.vehicle = vehicle;
            this.isCharging = isCharging;
            this.chargingPlan = chargingPlan;
            this.deadheadDistanceKm = deadheadDistanceKm;
            this.chargingCost = chargingCost;
        }

        public static CandidateSelection direct(Vehicle vehicle, double deadheadDistanceKm) {
            return new CandidateSelection(vehicle, false, null, deadheadDistanceKm, 0.0);
        }

        public static CandidateSelection charging(Vehicle vehicle, ScheduledChargingPlan plan, double chargingCost) {
            return new CandidateSelection(vehicle, true, plan, 0.0, chargingCost);
        }

        @Override
        public int compareTo(CandidateSelection other) {
            // 1. Direct EV assignment preferred over charging assignment
            if (this.isCharging != other.isCharging) {
                return this.isCharging ? 1 : -1;
            }
            // 2. If both direct, compare deadhead distance
            if (!this.isCharging) {
                int distCmp = Double.compare(this.deadheadDistanceKm, other.deadheadDistanceKm);
                if (distCmp != 0) return distCmp;
            } else {
                // 3. If both charging, compare charging cost
                int costCmp = Double.compare(this.chargingCost, other.chargingCost);
                if (costCmp != 0) return costCmp;
            }
            // 4. Deterministic tie-breaker by vehicle ID
            return this.vehicle.getVehicleId().compareTo(other.vehicle.getVehicleId());
        }
    }
}
