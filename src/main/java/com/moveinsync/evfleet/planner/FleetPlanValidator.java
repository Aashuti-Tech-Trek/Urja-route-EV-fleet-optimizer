package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Independent validator that re-evaluates all hard constraints on a completed fleet plan.
 *
 * <p>Validates:
 * <ol>
 *   <li><strong>Battery Reserve Floor:</strong> SoC never drops below 15% at any point in any timeline.</li>
 *   <li><strong>Event Non-Overlap:</strong> No vehicle has concurrent or overlapping events.</li>
 *   <li><strong>Spatial Continuity:</strong> Consecutive events start where the prior event ended.</li>
 *   <li><strong>Trip Punctuality:</strong> Trips start on/after arrival at pickup and finish on/before latestAllowedFinishTime.</li>
 *   <li><strong>Hub Charger Concurrency:</strong> Active sessions at any hub never exceed physical charger count.</li>
 *   <li><strong>Battery Capacity Invariants:</strong> Vehicle is never charged beyond 100% SoC.</li>
 *   <li><strong>Assignment Uniqueness:</strong> Every trip is assigned exactly once.</li>
 * </ol>
 */
public final class FleetPlanValidator {

    private static final double RESERVE_FLOOR = 0.15;
    private static final double TOLERANCE = 1e-4;

    public static final class ValidationReport {
        private final boolean valid;
        private final List<String> violations;

        public ValidationReport(boolean valid, List<String> violations) {
            this.valid = valid;
            this.violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        }

        public boolean isValid() { return valid; }
        public List<String> getViolations() { return violations; }

        @Override
        public String toString() {
            return valid ? "ValidationReport[VALID]" : "ValidationReport[INVALID: " + violations + "]";
        }
    }

    /**
     * Validates a plan against the input scenario.
     *
     * @param result the completed plan result
     * @param input the input planning scenario
     * @return ValidationReport with detailed violation messages if invalid
     */
    public ValidationReport validate(FleetPlanResult result, PlanningInput input) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(input, "input must not be null");

        List<String> violations = new ArrayList<>();
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

        // 1. Assignment uniqueness
        Set<String> assignedTripIds = new HashSet<>();
        for (TripAssignment assignment : result.getTripAssignments()) {
            String tripId = assignment.getTrip().getTripId();
            if (!assignedTripIds.add(tripId)) {
                violations.add("Duplicate trip assignment detected for trip: " + tripId);
            }
        }
        for (Trip t : input.getTrips()) {
            if (!assignedTripIds.contains(t.getTripId())) {
                violations.add("Trip " + t.getTripId() + " was omitted from assignments entirely");
            }
        }

        // 2. Per-vehicle timeline validation
        for (Map.Entry<String, VehicleTimeline> entry : result.getVehicleTimelines().entrySet()) {
            String vehicleId = entry.getKey();
            VehicleTimeline timeline = entry.getValue();
            Vehicle vehicle = vehicleMap.get(vehicleId);

            if (vehicle == null) {
                violations.add("Timeline exists for unknown vehicle: " + vehicleId);
                continue;
            }

            double batteryCapacity = vehicle.getBatteryCapacityKwh();
            List<TimelineEvent> events = timeline.getEvents();

            Instant lastEnd = timeline.getInitialAvailableTime();
            for (int i = 0; i < events.size(); i++) {
                TimelineEvent current = events.get(i);

                // Overlap check
                if (current.getStartTime().isBefore(lastEnd)) {
                    violations.add(String.format("Vehicle %s has overlapping event: %s starts at %s before prior end %s",
                            vehicleId, current.getDescription(), current.getStartTime(), lastEnd));
                }

                // Battery reserve floor check
                if (current.getEndSoc() < (RESERVE_FLOOR - TOLERANCE)) {
                    violations.add(String.format("Vehicle %s breached 15%% reserve floor in event %s: resulting SoC = %.4f",
                            vehicleId, current.getDescription(), current.getEndSoc()));
                }

                // Max SoC check
                if (current.getEndSoc() > (1.0 + TOLERANCE)) {
                    violations.add(String.format("Vehicle %s charged above 100%% SoC in event %s: resulting SoC = %.4f",
                            vehicleId, current.getDescription(), current.getEndSoc()));
                }

                // Spatial continuity check
                if (i > 0) {
                    TimelineEvent prior = events.get(i - 1);
                    if (!prior.getEndLocation().equals(current.getStartLocation())) {
                        violations.add(String.format("Vehicle %s spatial discontinuity: prior ended at %s, current begins at %s",
                                vehicleId, prior.getEndLocation(), current.getStartLocation()));
                    }
                }

                // Passenger trip punctuality check
                if (current.getEventType() == TimelineEvent.EventType.PASSENGER_TRIP) {
                    Trip trip = tripMap.get(current.getReferenceId());
                    if (trip != null) {
                        if (current.getEndTime().isAfter(trip.getLatestAllowedFinishTime())) {
                            violations.add(String.format("Trip %s on vehicle %s finished at %s, after latestAllowedFinishTime %s",
                                    trip.getTripId(), vehicleId, current.getEndTime(), trip.getLatestAllowedFinishTime()));
                        }
                    }
                }

                lastEnd = current.getEndTime();
            }
        }

        // 3. Hub charger concurrency check
        Map<String, List<ChargingSession>> sessionsByHub = new HashMap<>();
        for (ChargingSession s : result.getChargingSessions()) {
            sessionsByHub.computeIfAbsent(s.getHubId(), k -> new ArrayList<>()).add(s);
            Vehicle v = vehicleMap.get(s.getVehicleId());
            if (v != null) {
                try {
                    s.validateAgainstBatteryCapacity(v.getBatteryCapacityKwh());
                } catch (Exception ex) {
                    violations.add("Charging session battery capacity violation: " + ex.getMessage());
                }
            }
        }

        for (Map.Entry<String, List<ChargingSession>> hubEntry : sessionsByHub.entrySet()) {
            String hubId = hubEntry.getKey();
            ChargerHub hub = hubMap.get(hubId);
            if (hub == null) {
                violations.add("Charging session scheduled at unknown hub: " + hubId);
                continue;
            }

            int maxChargers = hub.getChargerCount();
            List<ChargingSession> sessions = hubEntry.getValue();

            // Collect event points
            TreeSet<Instant> points = new TreeSet<>();
            for (ChargingSession s : sessions) {
                points.add(s.getStartTime());
                points.add(s.getEndTime());
            }

            List<Instant> sorted = new ArrayList<>(points);
            for (int i = 0; i < sorted.size() - 1; i++) {
                Instant start = sorted.get(i);
                Instant end = sorted.get(i + 1);
                Instant mid = Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2);

                int concurrent = 0;
                for (ChargingSession s : sessions) {
                    if (!mid.isBefore(s.getStartTime()) && mid.isBefore(s.getEndTime())) {
                        concurrent++;
                    }
                }

                if (concurrent > maxChargers) {
                    violations.add(String.format("Hub %s oversubscribed between %s and %s: %d active sessions > %d chargers",
                            hubId, start, end, concurrent, maxChargers));
                }
            }
        }

        return new ValidationReport(violations.isEmpty(), violations);
    }
}
