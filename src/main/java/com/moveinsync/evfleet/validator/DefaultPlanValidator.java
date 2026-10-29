package com.moveinsync.evfleet.validator;

import com.moveinsync.evfleet.charging.ChargingCurve;
import com.moveinsync.evfleet.charging.TwoPartChargingCurve;
import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.energy.BatterySafetyCalculator;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.EnergyModel;
import com.moveinsync.evfleet.energy.StandardEnergyModel;
import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.PlanningInput;
import com.moveinsync.evfleet.planner.PlanningQualityReport;
import com.moveinsync.evfleet.planner.TimelineEvent;
import com.moveinsync.evfleet.planner.TripAssignment;
import com.moveinsync.evfleet.planner.VehicleTimeline;
import com.moveinsync.evfleet.travel.DefaultTravelTimeEstimator;
import com.moveinsync.evfleet.travel.DistanceMatrix;
import com.moveinsync.evfleet.travel.TravelConfig;
import com.moveinsync.evfleet.travel.TravelTimeEstimator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of the independent plan validator.
 *
 * <p>Audits fleet plans against all physical, temporal, operational, and financial hard constraints
 * without relying on the planner's internal feasibility flags or objective scores.
 *
 * <p><strong>Complexity:</strong>
 * <ul>
 *   <li>Trip Coverage & Assignment: O(T) where T = number of trips.</li>
 *   <li>Per-Vehicle Timelines: O(V * E) where V = vehicles, E = average events per vehicle.</li>
 *   <li>Hub Concurrency: O(H * S log S) sweep-line where H = hubs, S = charging sessions at hub.</li>
 *   <li>Cost Reconciliation: O(S) where S = total charging sessions.</li>
 * </ul>
 */
public final class DefaultPlanValidator implements PlanValidator {

    public static final double RESERVE_FLOOR = 0.1500;
    public static final double SOC_EPSILON = 1e-4;
    public static final double CAPACITY_TOLERANCE = 1e-4;
    public static final double ENERGY_TOLERANCE_KWH = 0.05;
    public static final BigDecimal COST_TOLERANCE = new BigDecimal("0.05");

    private final EnergyModel energyModel;
    private final EnergyFactors energyFactors;
    private final TravelTimeEstimator travelEstimator;
    private final DistanceMatrix distanceMatrix;
    private final ChargingCurve chargingCurve;

    public DefaultPlanValidator() {
        this(new StandardEnergyModel(),
                EnergyFactors.standard(),
                new DefaultTravelTimeEstimator(TravelConfig.defaultConfig()),
                new DistanceMatrix(new DefaultTravelTimeEstimator(TravelConfig.defaultConfig())),
                new TwoPartChargingCurve());
    }

    public DefaultPlanValidator(EnergyModel energyModel,
                                EnergyFactors energyFactors,
                                TravelTimeEstimator travelEstimator,
                                DistanceMatrix distanceMatrix,
                                ChargingCurve chargingCurve) {
        this.energyModel = energyModel != null ? energyModel : new StandardEnergyModel();
        this.energyFactors = energyFactors != null ? energyFactors : EnergyFactors.standard();
        this.travelEstimator = travelEstimator != null ? travelEstimator : new DefaultTravelTimeEstimator(TravelConfig.defaultConfig());
        this.distanceMatrix = distanceMatrix != null ? distanceMatrix : new DistanceMatrix(this.travelEstimator);
        this.chargingCurve = chargingCurve != null ? chargingCurve : new TwoPartChargingCurve();
    }

    @Override
    public ValidationReport validate(FleetPlanResult plan, PlanningInput input) {
        long startNanos = System.nanoTime();
        List<PlanViolation> violations = new ArrayList<>();
        int checksExecuted = 0;
        int incompleteChecks = 0;

        // 1. Input Integrity and Completeness Preconditions
        if (plan == null) {
            violations.add(PlanViolation.of(ViolationCode.MISSING_VALIDATION_DATA, "GLOBAL", "FleetPlanResult is null"));
            return ValidationReport.indeterminate(1, 1, violations, Duration.ofNanos(System.nanoTime() - startNanos));
        }
        if (input == null) {
            violations.add(PlanViolation.of(ViolationCode.MISSING_VALIDATION_DATA, "GLOBAL", "PlanningInput is null"));
            return ValidationReport.indeterminate(1, 1, violations, Duration.ofNanos(System.nanoTime() - startNanos));
        }

        if (plan.getTripAssignments() == null || plan.getVehicleTimelines() == null || plan.getChargingSessions() == null) {
            violations.add(PlanViolation.of(ViolationCode.MISSING_VALIDATION_DATA, "GLOBAL", "FleetPlanResult contains null collection fields"));
            return ValidationReport.indeterminate(2, 1, violations, Duration.ofNanos(System.nanoTime() - startNanos));
        }

        Map<String, Vehicle> vehicleMap = new HashMap<>();
        for (Vehicle v : input.getVehicles()) {
            vehicleMap.put(v.getVehicleId(), v);
        }

        Map<String, ChargerHub> hubMap = new HashMap<>();
        for (ChargerHub h : input.getHubs()) {
            hubMap.put(h.getHubId(), h);
        }

        Map<String, Trip> tripMap = new HashMap<>();
        for (Trip t : input.getTrips()) {
            tripMap.put(t.getTripId(), t);
        }

        // 2. Trip Coverage & Assignment Uniqueness Verification
        checksExecuted++;
        Set<String> assignedTripIds = new HashSet<>();
        int actualBackupCount = 0;
        int actualEvCount = 0;

        for (TripAssignment assignment : plan.getTripAssignments()) {
            if (assignment == null || assignment.getTrip() == null) {
                violations.add(PlanViolation.of(ViolationCode.MISSING_VALIDATION_DATA, "GLOBAL", "Trip assignment or assigned trip is null"));
                continue;
            }

            String tripId = assignment.getTrip().getTripId();
            if (!tripMap.containsKey(tripId)) {
                violations.add(PlanViolation.of(ViolationCode.UNKNOWN_ENTITY_REFERENCED, tripId,
                        "Assignment references unrecognized trip ID: " + tripId));
            }

            if (!assignedTripIds.add(tripId)) {
                violations.add(PlanViolation.of(ViolationCode.DUPLICATE_TRIP_ASSIGNMENT, tripId,
                        "Trip " + tripId + " is assigned more than once across plan assignments"));
            }

            if (assignment.isEvAssignment()) {
                actualEvCount++;
                Vehicle assignedV = assignment.getAssignedVehicle();
                if (assignedV == null || !vehicleMap.containsKey(assignedV.getVehicleId())) {
                    violations.add(PlanViolation.of(ViolationCode.UNKNOWN_ENTITY_REFERENCED, tripId,
                            "EV assignment for trip " + tripId + " references unknown or null vehicle"));
                }
            } else if (assignment.isBackupAssignment()) {
                actualBackupCount++;
                if (assignment.getBackupReason() == null) {
                    violations.add(PlanViolation.of(ViolationCode.MISSING_VALIDATION_DATA, tripId,
                            "Backup assignment for trip " + tripId + " lacks an explicit rejection reason"));
                }
            }
        }

        for (Trip t : input.getTrips()) {
            if (!assignedTripIds.contains(t.getTripId())) {
                violations.add(PlanViolation.of(ViolationCode.TRIP_UNASSIGNED, t.getTripId(),
                        "Trip " + t.getTripId() + " was requested but completely omitted from plan assignments"));
            }
        }

        // 3. Per-Vehicle Timeline & Battery Reserve Verification
        checksExecuted++;
        for (Map.Entry<String, VehicleTimeline> entry : plan.getVehicleTimelines().entrySet()) {
            String vehicleId = entry.getKey();
            VehicleTimeline timeline = entry.getValue();
            Vehicle vehicle = vehicleMap.get(vehicleId);

            if (vehicle == null) {
                violations.add(PlanViolation.of(ViolationCode.UNKNOWN_ENTITY_REFERENCED, vehicleId,
                        "Plan contains timeline for unknown vehicle: " + vehicleId));
                continue;
            }

            double batteryCapacity = vehicle.getBatteryCapacityKwh();
            double vehicleBaseConsumption = vehicle.getBaseConsumptionKwhPerKm();
            List<TimelineEvent> events = timeline.getEvents();

            Instant lastEnd = timeline.getInitialAvailableTime();
            if (timeline.getInitialAvailableTime().isBefore(vehicle.getAvailableFrom())) {
                violations.add(PlanViolation.of(ViolationCode.PICKUP_TIME_INFEASIBLE, vehicleId,
                        timeline.getInitialAvailableTime(),
                        timeline.getInitialAvailableTime().toString(),
                        vehicle.getAvailableFrom().toString(),
                        "Vehicle timeline starts before vehicle availableFrom"));
            }

            double expectedRunningSoc = timeline.getInitialSoc();

            for (int i = 0; i < events.size(); i++) {
                TimelineEvent current = events.get(i);
                checksExecuted++;

                // Event internal duration
                if (current.getEndTime().isBefore(current.getStartTime())) {
                    violations.add(PlanViolation.of(ViolationCode.VEHICLE_TIMELINE_OVERLAP, vehicleId,
                            current.getStartTime(), current.getEndTime().toString(), current.getStartTime().toString(),
                            "Event end time is before start time in event " + current.getDescription()));
                }

                // Chronological continuity / non-overlap
                if (current.getStartTime().isBefore(lastEnd)) {
                    violations.add(PlanViolation.of(ViolationCode.VEHICLE_TIMELINE_OVERLAP, vehicleId,
                            current.getStartTime(), current.getStartTime().toString(), lastEnd.toString(),
                            String.format("Vehicle %s has overlapping event: '%s' starts at %s before prior end %s",
                                    vehicleId, current.getDescription(), current.getStartTime(), lastEnd)));
                }

                // Spatial continuity
                if (i > 0) {
                    TimelineEvent prior = events.get(i - 1);
                    if (!prior.getEndLocation().equals(current.getStartLocation())) {
                        violations.add(PlanViolation.of(ViolationCode.SPATIAL_DISCONTINUITY, vehicleId,
                                current.getStartTime(), current.getStartLocation().toString(), prior.getEndLocation().toString(),
                                String.format("Vehicle %s spatial discontinuity: event '%s' starts at %s but prior '%s' ended at %s",
                                        vehicleId, current.getDescription(), current.getStartLocation(),
                                        prior.getDescription(), prior.getEndLocation())));
                    }
                }

                // Battery Reserve Floor check: MUST BE >= 15% (0.1500)
                if (current.getEndSoc() < (RESERVE_FLOOR - 1e-6)) {
                    violations.add(PlanViolation.of(ViolationCode.BATTERY_RESERVE_VIOLATION, vehicleId,
                            current.getEndTime(),
                            String.format("%.4f", current.getEndSoc()),
                            String.format(">= %.4f", RESERVE_FLOOR),
                            String.format("Vehicle %s breached 15%% reserve floor in event '%s': resulting SoC = %.4f",
                                    vehicleId, current.getDescription(), current.getEndSoc())));
                }

                // Maximum Battery Capacity check: MUST BE <= 100% (1.0000)
                if (current.getEndSoc() > (1.0 + CAPACITY_TOLERANCE)) {
                    violations.add(PlanViolation.of(ViolationCode.BATTERY_CAPACITY_EXCEEDED, vehicleId,
                            current.getEndTime(),
                            String.format("%.4f", current.getEndSoc()),
                            "<= 1.0000",
                            String.format("Vehicle %s charged above 100%% SoC in event '%s': resulting SoC = %.4f",
                                    vehicleId, current.getDescription(), current.getEndSoc())));
                }

                // Energy Accounting Reconciliation
                double reportedDeltaKwh = current.getEnergyDeltaKwh();
                double expectedDeltaSoc = reportedDeltaKwh / batteryCapacity;
                double expectedEndSocFromReported = current.getStartSoc() + expectedDeltaSoc;

                if (Math.abs(current.getEndSoc() - expectedEndSocFromReported) > SOC_EPSILON) {
                    violations.add(PlanViolation.of(ViolationCode.ENERGY_ACCOUNTING_MISMATCH, vehicleId,
                            current.getEndTime(),
                            String.format("endSoc=%.4f", current.getEndSoc()),
                            String.format("expected=%.4f from startSoc=%.4f + delta=%.4f kWh",
                                    expectedEndSocFromReported, current.getStartSoc(), reportedDeltaKwh),
                            "Recorded SoC does not reconcile with event energy delta"));
                }

                // Travel-specific checks
                if (current.getEventType() == TimelineEvent.EventType.PASSENGER_TRIP ||
                        current.getEventType() == TimelineEvent.EventType.EMPTY_DRIVE) {
                    if (reportedDeltaKwh > 0.0001) {
                        violations.add(PlanViolation.of(ViolationCode.ENERGY_ACCOUNTING_MISMATCH, vehicleId,
                                current.getStartTime(),
                                String.format("+%.4f kWh", reportedDeltaKwh),
                                "<= 0.0 kWh",
                                "Vehicle movement event has positive energy delta: " + current.getDescription()));
                    }

                    if (current.getDistanceKm() > 0.0) {
                        double expectedConsumedKwh = energyModel.calculateEnergyRequired(
                                current.getDistanceKm(), vehicleBaseConsumption, energyFactors);
                        double actualConsumedKwh = -reportedDeltaKwh;
                        if (Math.abs(actualConsumedKwh - expectedConsumedKwh) > ENERGY_TOLERANCE_KWH) {
                            violations.add(PlanViolation.of(ViolationCode.ENERGY_ACCOUNTING_MISMATCH, vehicleId,
                                    current.getStartTime(),
                                    String.format("%.4f kWh", actualConsumedKwh),
                                    String.format("%.4f kWh", expectedConsumedKwh),
                                    "Physical movement energy does not match distance and vehicle base consumption"));
                        }
                    }
                }

                // Passenger trip punctuality & deadline
                if (current.getEventType() == TimelineEvent.EventType.PASSENGER_TRIP) {
                    Trip trip = tripMap.get(current.getReferenceId());
                    if (trip != null) {
                        if (current.getEndTime().isAfter(trip.getLatestAllowedFinishTime())) {
                            violations.add(PlanViolation.of(ViolationCode.TRIP_DEADLINE_VIOLATION, trip.getTripId(),
                                    current.getEndTime(),
                                    current.getEndTime().toString(),
                                    trip.getLatestAllowedFinishTime().toString(),
                                    String.format("Trip %s on vehicle %s finished at %s, after latestAllowedFinishTime %s",
                                            trip.getTripId(), vehicleId, current.getEndTime(), trip.getLatestAllowedFinishTime())));
                        }
                    }
                }

                lastEnd = current.getEndTime();
            }
        }

        // 4. Charger Capacity & Concurrency Verification (Sweep-Line Algorithm)
        checksExecuted++;
        Map<String, List<ChargingSession>> sessionsByHub = new HashMap<>();
        Map<String, List<ChargingSession>> sessionsByVehicle = new HashMap<>();

        for (ChargingSession s : plan.getChargingSessions()) {
            if (s == null) continue;
            sessionsByHub.computeIfAbsent(s.getHubId(), k -> new ArrayList<>()).add(s);
            sessionsByVehicle.computeIfAbsent(s.getVehicleId(), k -> new ArrayList<>()).add(s);

            Vehicle v = vehicleMap.get(s.getVehicleId());
            if (v == null) {
                violations.add(PlanViolation.of(ViolationCode.UNKNOWN_ENTITY_REFERENCED, s.getVehicleId(),
                        "Charging session " + s.getSessionId() + " references unknown vehicle: " + s.getVehicleId()));
            } else {
                if (s.getEndSoc() > (1.0 + CAPACITY_TOLERANCE)) {
                    violations.add(PlanViolation.of(ViolationCode.BATTERY_CAPACITY_EXCEEDED, s.getVehicleId(),
                            s.getEndTime(), String.format("%.4f", s.getEndSoc()), "<= 1.0000",
                            "Charging session exceeds 100% SoC for vehicle " + s.getVehicleId()));
                }
            }

            if (!s.getEndTime().isAfter(s.getStartTime())) {
                violations.add(PlanViolation.of(ViolationCode.CHARGING_SESSION_INVALID, s.getSessionId(),
                        s.getStartTime(), s.getEndTime().toString(), "after " + s.getStartTime(),
                        "Session end time is not strictly after start time"));
            }
        }

        // Verify charging session does not overlap with travel on same vehicle
        for (Map.Entry<String, List<ChargingSession>> entry : sessionsByVehicle.entrySet()) {
            String vId = entry.getKey();
            VehicleTimeline timeline = plan.getVehicleTimelines().get(vId);
            if (timeline == null) continue;

            for (ChargingSession s : entry.getValue()) {
                for (TimelineEvent ev : timeline.getEvents()) {
                    if (ev.getEventType() == TimelineEvent.EventType.PASSENGER_TRIP ||
                            ev.getEventType() == TimelineEvent.EventType.EMPTY_DRIVE) {
                        // Check overlap
                        if (s.getStartTime().isBefore(ev.getEndTime()) && ev.getStartTime().isBefore(s.getEndTime())) {
                            violations.add(PlanViolation.of(ViolationCode.CHARGING_DURING_TRIP, vId,
                                    s.getStartTime(),
                                    String.format("Session [%s, %s)", s.getStartTime(), s.getEndTime()),
                                    String.format("Movement [%s, %s)", ev.getStartTime(), ev.getEndTime()),
                                    "Vehicle scheduled for charging during movement event: " + ev.getDescription()));
                        }
                    }
                }
            }
        }

        // Hub Concurrency Interval Sweep: O(N log N)
        for (Map.Entry<String, List<ChargingSession>> hubEntry : sessionsByHub.entrySet()) {
            String hubId = hubEntry.getKey();
            ChargerHub hub = hubMap.get(hubId);

            if (hub == null) {
                violations.add(PlanViolation.of(ViolationCode.UNKNOWN_ENTITY_REFERENCED, hubId,
                        "Charging sessions scheduled at unrecognized hub: " + hubId));
                continue;
            }

            int chargerCount = hub.getChargerCount();
            List<ChargingSession> sessions = hubEntry.getValue();

            // Create sweep endpoints: half-open interval [start, end)
            // For equal timestamps, END (-1) events MUST be processed before START (+1) events
            List<SweepEvent> sweepEvents = new ArrayList<>();
            for (ChargingSession s : sessions) {
                sweepEvents.add(new SweepEvent(s.getStartTime(), SweepType.START, s));
                sweepEvents.add(new SweepEvent(s.getEndTime(), SweepType.END, s));
            }

            sweepEvents.sort(Comparator
                    .comparing((SweepEvent se) -> se.time)
                    .thenComparing(se -> se.type == SweepType.END ? 0 : 1));

            int activeCount = 0;
            for (SweepEvent se : sweepEvents) {
                if (se.type == SweepType.START) {
                    activeCount++;
                    if (activeCount > chargerCount) {
                        violations.add(PlanViolation.of(ViolationCode.CHARGER_CAPACITY_EXCEEDED, hubId,
                                se.time,
                                String.valueOf(activeCount),
                                String.valueOf(chargerCount),
                                String.format("Hub %s oversubscribed at %s: %d active sessions exceed %d physical chargers",
                                        hubId, se.time, activeCount, chargerCount)));
                    }
                } else {
                    activeCount--;
                }
            }
        }

        // 5. Tariff & Charging Cost Recalculation
        checksExecuted++;
        BigDecimal expectedTotalCost = BigDecimal.ZERO;
        for (ChargingSession s : plan.getChargingSessions()) {
            ChargerHub hub = hubMap.get(s.getHubId());
            if (hub != null && hub.getTariffSchedule() != null) {
                BigDecimal expectedSessionCost = hub.getTariffSchedule().calculateCost(
                        s.getStartTime(), s.getEndTime(), s.getEnergyDeliveredKwh());
                expectedTotalCost = expectedTotalCost.add(s.getEstimatedCost());

                if (s.getEstimatedCost().subtract(expectedSessionCost).abs().compareTo(COST_TOLERANCE) > 0) {
                    violations.add(PlanViolation.of(ViolationCode.CHARGING_COST_MISMATCH, s.getSessionId(),
                            s.getStartTime(),
                            s.getEstimatedCost().toString(),
                            expectedSessionCost.toString(),
                            "Charging session cost does not match tariff calculation"));
                }
            }
        }

        if (plan.getTotalChargingCost().subtract(expectedTotalCost).abs().compareTo(COST_TOLERANCE) > 0) {
            violations.add(PlanViolation.of(ViolationCode.CHARGING_COST_MISMATCH, "GLOBAL",
                    null,
                    plan.getTotalChargingCost().toString(),
                    expectedTotalCost.toString(),
                    "Total plan charging cost does not equal sum of individual charging session costs"));
        }

        // 6. Plan Metric Inconsistency Checks
        checksExecuted++;
        if (plan.getTotalBackupTripCount() != actualBackupCount) {
            violations.add(PlanViolation.of(ViolationCode.METRIC_INCONSISTENCY, "GLOBAL",
                    null,
                    String.valueOf(plan.getTotalBackupTripCount()),
                    String.valueOf(actualBackupCount),
                    "Reported total backup trip count does not match actual count of backup trip assignments"));
        }

        PlanningQualityReport qualityReport = plan.getQualityReport();
        if (qualityReport != null) {
            if (qualityReport.getTripsRequested() != input.getTrips().size()) {
                violations.add(PlanViolation.of(ViolationCode.METRIC_INCONSISTENCY, "GLOBAL",
                        null,
                        String.valueOf(qualityReport.getTripsRequested()),
                        String.valueOf(input.getTrips().size()),
                        "Quality report tripsRequested does not match input trips count"));
            }
            if (qualityReport.getTripsAssignedToBackup() != actualBackupCount) {
                violations.add(PlanViolation.of(ViolationCode.METRIC_INCONSISTENCY, "GLOBAL",
                        null,
                        String.valueOf(qualityReport.getTripsAssignedToBackup()),
                        String.valueOf(actualBackupCount),
                        "Quality report tripsAssignedToBackup does not match actual count of backup assignments"));
            }
            if (qualityReport.getTripsAssignedToEv() != actualEvCount) {
                violations.add(PlanViolation.of(ViolationCode.METRIC_INCONSISTENCY, "GLOBAL",
                        null,
                        String.valueOf(qualityReport.getTripsAssignedToEv()),
                        String.valueOf(actualEvCount),
                        "Quality report tripsAssignedToEv does not match actual count of EV assignments"));
            }
        }

        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        if (violations.isEmpty()) {
            return ValidationReport.valid(checksExecuted, duration);
        } else {
            return ValidationReport.invalid(checksExecuted, violations, duration);
        }
    }

    private enum SweepType { START, END }

    private static final class SweepEvent {
        private final Instant time;
        private final SweepType type;
        private final ChargingSession session;

        private SweepEvent(Instant time, SweepType type, ChargingSession session) {
            this.time = time;
            this.type = type;
            this.session = session;
        }
    }
}
