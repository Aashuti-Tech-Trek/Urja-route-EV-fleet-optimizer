package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.location.Location;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the chronological sequence of scheduled events for a vehicle during a planning run.
 *
 * <p>Enforces invariant continuity:
 * <ul>
 *   <li>Events must be strictly chronological and non-overlapping.</li>
 *   <li>Battery state of charge (SoC) must never drop below the 15% reserve floor.</li>
 *   <li>Provides snapshot capability so candidate evaluation can simulate additions without mutating accepted state.</li>
 * </ul>
 */
public final class VehicleTimeline {

    private static final double RESERVE_FLOOR = 0.15;
    private static final double SOC_TOLERANCE = 1e-4;

    private final String vehicleId;
    private final Location initialLocation;
    private final Instant initialAvailableTime;
    private final double initialSoc;
    private final double batteryCapacityKwh;
    private final List<TimelineEvent> events;

    public VehicleTimeline(String vehicleId, Location initialLocation,
                           Instant initialAvailableTime, double initialSoc,
                           double batteryCapacityKwh) {
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        this.initialLocation = Objects.requireNonNull(initialLocation, "initialLocation must not be null");
        this.initialAvailableTime = Objects.requireNonNull(initialAvailableTime, "initialAvailableTime must not be null");
        this.initialSoc = initialSoc;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.events = new ArrayList<>();
    }

    public VehicleTimeline(String vehicleId, Location initialLocation,
                            Instant initialAvailableTime, double initialSoc,
                            double batteryCapacityKwh, List<TimelineEvent> events) {
        this.vehicleId = vehicleId;
        this.initialLocation = initialLocation;
        this.initialAvailableTime = initialAvailableTime;
        this.initialSoc = initialSoc;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.events = new ArrayList<>(events);
    }

    /**
     * Creates an isolated snapshot copy of this timeline for speculative evaluation.
     */
    public VehicleTimeline copy() {
        return new VehicleTimeline(vehicleId, initialLocation, initialAvailableTime, initialSoc, batteryCapacityKwh, events);
    }

    /**
     * Appends an event to the vehicle's timeline.
     *
     * @param event the event to record
     * @throws IllegalStateException if event overlaps previous events or drops SoC below reserve
     */
    public void addEvent(TimelineEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        Instant currentAvailable = getCurrentAvailableTime();
        if (event.getStartTime().isBefore(currentAvailable)) {
            throw new IllegalStateException(String.format(
                    "Vehicle %s event start time (%s) is before vehicle current available time (%s)",
                    vehicleId, event.getStartTime(), currentAvailable));
        }

        if (event.getEndSoc() < (RESERVE_FLOOR - SOC_TOLERANCE)) {
            throw new IllegalStateException(String.format(
                    "Vehicle %s event %s breaches 15%% reserve floor: resulting SoC = %.4f",
                    vehicleId, event.getEventType(), event.getEndSoc()));
        }

        events.add(event);
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public Location getInitialLocation() {
        return initialLocation;
    }

    public Instant getInitialAvailableTime() {
        return initialAvailableTime;
    }

    public double getInitialSoc() {
        return initialSoc;
    }

    public double getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public List<TimelineEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public Location getCurrentLocation() {
        if (events.isEmpty()) {
            return initialLocation;
        }
        return events.get(events.size() - 1).getEndLocation();
    }

    public Instant getCurrentAvailableTime() {
        if (events.isEmpty()) {
            return initialAvailableTime;
        }
        return events.get(events.size() - 1).getEndTime();
    }

    public double getCurrentSoc() {
        if (events.isEmpty()) {
            return initialSoc;
        }
        return events.get(events.size() - 1).getEndSoc();
    }

    public double getTotalDistanceKm() {
        return events.stream().mapToDouble(e -> e.getDistanceKm()).sum();
    }

    public double getTotalEnergyConsumedKwh() {
        return events.stream()
                .filter(e -> e.getEnergyDeltaKwh() < 0)
                .mapToDouble(e -> -e.getEnergyDeltaKwh())
                .sum();
    }

    public double getTotalEnergyChargedKwh() {
        return events.stream()
                .filter(e -> e.getEnergyDeltaKwh() > 0)
                .mapToDouble(e -> e.getEnergyDeltaKwh())
                .sum();
    }

    @Override
    public String toString() {
        return String.format("VehicleTimeline[vehicle=%s, events=%d, currentLoc=%s, availableAt=%s, soc=%.2f%%]",
                vehicleId, events.size(), getCurrentLocation(), getCurrentAvailableTime(), getCurrentSoc() * 100);
    }
}
