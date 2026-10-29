package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.charging.TwoPartChargingCurve;
import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.domain.VehicleAvailability;
import com.moveinsync.evfleet.domain.VehicleState;
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

        // 1. Initial sort by urgency and difficulty (scheduledStartTime asc, latestFinish asc, distanceKm desc, tripId asc)
        List<Trip> baselineTrips = new ArrayList<>(input.getTrips());
        baselineTrips.sort(Comparator
                .comparing((Trip t) -> t.getScheduledStartTime())
                .thenComparing(t -> t.getLatestAllowedFinishTime())
                .thenComparing(Comparator.comparingDouble((Trip t) -> t.getDistanceKm()).reversed())
                .thenComparing(t -> t.getTripId()));

        // 2. Build initial greedy plan
        PlanBuild bestBuild = buildPlan(baselineTrips, input);
        int totalCandidateEvaluations = bestBuild.candidateEvaluations;
        int localSearchIterations = 0;

        // 3. Bounded Local Search (explores priority perturbations for unassigned backup trips or cost improvements)
        if (localSearchConfig.isEnabled() && localSearchConfig.getMaxIterations() > 0 && baselineTrips.size() > 1) {
            long maxDurationNanos = localSearchConfig.getTimeoutMillis() * 1_000_000L;
            List<Trip> currentOrder = new ArrayList<>(baselineTrips);

            // Identify backup trip indices
            for (int i = 0; i < currentOrder.size() && localSearchIterations < localSearchConfig.getMaxIterations(); i++) {
                if (System.nanoTime() - startNanos >= maxDurationNanos && maxDurationNanos > 0) {
                    break;
                }

                Trip currentTrip = currentOrder.get(i);
                boolean isBackup = bestBuild.assignments.stream()
                        .anyMatch(a -> a.getTrip().getTripId().equals(currentTrip.getTripId()) && a.isBackupAssignment());

                if (isBackup && i > 0) {
                    // Try promoting this backup trip ahead of competing earlier trips
                    for (int targetIdx = 0; targetIdx < i && localSearchIterations < localSearchConfig.getMaxIterations(); targetIdx++) {
                        if (System.nanoTime() - startNanos >= maxDurationNanos && maxDurationNanos > 0) {
                            break;
                        }

                        localSearchIterations++;
                        List<Trip> candidateOrder = new ArrayList<>(currentOrder);
                        candidateOrder.remove(i);
                        candidateOrder.add(targetIdx, currentTrip);

                        PlanBuild candidateBuild = buildPlan(candidateOrder, input);
                        totalCandidateEvaluations += candidateBuild.candidateEvaluations;

                        // Validate candidate plan independently
                        FleetPlanResult tempResult = createTempResult(input, candidateBuild, Duration.ZERO);
                        FleetPlanValidator.ValidationReport validation = validator.validate(tempResult, input);

                        if (validation.isValid()) {
                            // Lexicographic check: 1. Fewer backup trips; 2. Same backup trips & lower cost
                            if (candidateBuild.backupCount < bestBuild.backupCount) {
                                bestBuild = candidateBuild;
                                currentOrder = candidateOrder;
                                break;
                            } else if (candidateBuild.backupCount == bestBuild.backupCount
                                    && candidateBuild.totalCost.compareTo(bestBuild.totalCost) < 0) {
                                bestBuild = candidateBuild;
                                currentOrder = candidateOrder;
                            }
                        }
                    }
                }
            }
        }

        // 4. Assemble final result
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        String currency = bestBuild.sessions.isEmpty()
                ? (input.getHubs().isEmpty() ? "INR" : input.getHubs().get(0).getTariffSchedule().getCurrency())
                : bestBuild.sessions.get(0).getCurrency();

        FleetPlanResult.PlanStatus planStatus = bestBuild.backupCount == 0
                ? FleetPlanResult.PlanStatus.SUCCESS
                : (bestBuild.backupCount < baselineTrips.size()
                ? FleetPlanResult.PlanStatus.PARTIAL_COVERAGE
                : FleetPlanResult.PlanStatus.NO_FEASIBLE_ASSIGNMENT);

        int backupLowerBound = input.getVehicles().isEmpty() ? baselineTrips.size() : 0;
        String lowerBoundAssumptions = "Defensible lower bound assumes ideal vehicle range and zero charger queuing.";

        FleetPlanResult unvalidatedResult = new FleetPlanResult(
                input.getPlanningRunId(),
                planStatus,
                bestBuild.assignments,
                bestBuild.timelines,
                bestBuild.sessions,
                bestBuild.backupCount,
                bestBuild.totalCost,
                currency,
                bestBuild.costByHub,
                duration,
                HEURISTIC_VERSION,
                "UNVALIDATED",
                null
        );

        com.moveinsync.evfleet.validator.ValidationReport detailedReport = validator.validateDetailed(unvalidatedResult, input);
        String validationStatus = detailedReport.isValid() ? "VALIDATED" : "VIOLATIONS_DETECTED";

        PlanningQualityReport qualityReport = new PlanningQualityReport(
                baselineTrips.size(),
                baselineTrips.size() - bestBuild.backupCount,
                bestBuild.backupCount,
                bestBuild.totalCost,
                currency,
                bestBuild.sessions.size(),
                bestBuild.sessionsByHub,
                duration,
                localSearchIterations,
                totalCandidateEvaluations,
                detailedReport.isValid(),
                backupLowerBound,
                lowerBoundAssumptions
        );

        return new FleetPlanResult(
                input.getPlanningRunId(),
                planStatus,
                bestBuild.assignments,
                bestBuild.timelines,
                bestBuild.sessions,
                bestBuild.backupCount,
                bestBuild.totalCost,
                currency,
                bestBuild.costByHub,
                duration,
                HEURISTIC_VERSION,
                validationStatus,
                qualityReport,
                detailedReport
        );
    }

    private PlanBuild buildPlan(List<Trip> trips, PlanningInput input) {
        List<Vehicle> sortedVehicles = new ArrayList<>(input.getVehicles());
        sortedVehicles.sort(Comparator.comparing(v -> v.getVehicleId()));

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

        for (Trip trip : trips) {
            CandidateSelection bestCandidate = null;
            FeasibilityReason primaryRejectionReason = FeasibilityReason.NONE;
            String primaryRejectionDetail = null;

            for (Vehicle v : sortedVehicles) {
                candidateEvaluations++;
                VehicleTimeline timeline = timelines.get(v.getVehicleId());
                Vehicle dynamicVehicle = createDynamicVehicle(v, timeline);

                FeasibilityResult directCheck = feasibilityChecker.check(dynamicVehicle, trip);
                if (directCheck.isFeasible()) {
                    double deadheadDist = directCheck.getEmptyDriveDistanceKm().orElse(0.0);
                    CandidateSelection candidate = CandidateSelection.direct(v, deadheadDist);
                    if (bestCandidate == null || candidate.compareTo(bestCandidate) < 0) {
                        bestCandidate = candidate;
                    }
                } else {
                    if (primaryRejectionReason == FeasibilityReason.NONE
                            || directCheck.getReason() == FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP
                            || directCheck.getReason() == FeasibilityReason.DEADLINE_INFEASIBLE) {
                        primaryRejectionReason = directCheck.getReason();
                        primaryRejectionDetail = directCheck.getExplanation();
                    }

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

            if (bestCandidate != null) {
                Vehicle assignedVehicle = bestCandidate.vehicle;
                VehicleTimeline timeline = timelines.get(assignedVehicle.getVehicleId());

                if (bestCandidate.chargingPlan != null) {
                    ScheduledChargingPlan cp = bestCandidate.chargingPlan;
                    if (cp.getDeadheadEvent().isPresent()) {
                        timeline.addEvent(cp.getDeadheadEvent().get());
                    }
                    timeline.addEvent(cp.getChargingEvent().orElseThrow());
                    ChargingSession cs = cp.getSession().orElseThrow();
                    sessions.add(cs);
                    occupancyTracker.recordSession(cs);
                }

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
                FeasibilityReason reason = primaryRejectionReason != FeasibilityReason.NONE
                        ? primaryRejectionReason : FeasibilityReason.VEHICLE_UNAVAILABLE;
                String detail = primaryRejectionDetail != null
                        ? primaryRejectionDetail : "No feasible EV candidate found by heuristic";
                assignments.add(TripAssignment.toBackup(trip, reason, detail));
            }
        }

        int backupCount = (int) assignments.stream().filter(a -> a.isBackupAssignment()).count();
        BigDecimal totalCost = sessions.stream()
                .map(s -> s.getEstimatedCost())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        Map<String, BigDecimal> costByHub = new HashMap<>();
        Map<String, Integer> sessionsByHub = new HashMap<>();
        for (ChargingSession s : sessions) {
            costByHub.merge(s.getHubId(), s.getEstimatedCost(), (a, b) -> a.add(b));
            sessionsByHub.merge(s.getHubId(), 1, (a, b) -> a + b);
        }

        return new PlanBuild(assignments, timelines, sessions, costByHub, sessionsByHub, candidateEvaluations, backupCount, totalCost);
    }

    private FleetPlanResult createTempResult(PlanningInput input, PlanBuild build, Duration duration) {
        String currency = build.sessions.isEmpty()
                ? (input.getHubs().isEmpty() ? "INR" : input.getHubs().get(0).getTariffSchedule().getCurrency())
                : build.sessions.get(0).getCurrency();
        return new FleetPlanResult(
                input.getPlanningRunId(),
                build.backupCount == 0 ? FleetPlanResult.PlanStatus.SUCCESS : FleetPlanResult.PlanStatus.PARTIAL_COVERAGE,
                build.assignments,
                build.timelines,
                build.sessions,
                build.backupCount,
                build.totalCost,
                currency,
                build.costByHub,
                duration,
                HEURISTIC_VERSION,
                "UNVALIDATED",
                null
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
                continue;
            }

            ScheduledChargingPlan plan = chargingScheduler.planCharging(
                    vehicle, timeline, hub, targetSoc, chargingDeadline, tracker);

            if (plan.isFeasible()) {
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

    private static final class PlanBuild {
        private final List<TripAssignment> assignments;
        private final Map<String, VehicleTimeline> timelines;
        private final List<ChargingSession> sessions;
        private final Map<String, BigDecimal> costByHub;
        private final Map<String, Integer> sessionsByHub;
        private final int candidateEvaluations;
        private final int backupCount;
        private final BigDecimal totalCost;

        private PlanBuild(List<TripAssignment> assignments, Map<String, VehicleTimeline> timelines,
                          List<ChargingSession> sessions, Map<String, BigDecimal> costByHub,
                          Map<String, Integer> sessionsByHub, int candidateEvaluations,
                          int backupCount, BigDecimal totalCost) {
            this.assignments = assignments;
            this.timelines = timelines;
            this.sessions = sessions;
            this.costByHub = costByHub;
            this.sessionsByHub = sessionsByHub;
            this.candidateEvaluations = candidateEvaluations;
            this.backupCount = backupCount;
            this.totalCost = totalCost;
        }
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
            if (this.isCharging != other.isCharging) {
                return this.isCharging ? 1 : -1;
            }
            if (!this.isCharging) {
                int distCmp = Double.compare(this.deadheadDistanceKm, other.deadheadDistanceKm);
                if (distCmp != 0) return distCmp;
            } else {
                int costCmp = Double.compare(this.chargingCost, other.chargingCost);
                if (costCmp != 0) return costCmp;
            }
            return this.vehicle.getVehicleId().compareTo(other.vehicle.getVehicleId());
        }
    }
}
