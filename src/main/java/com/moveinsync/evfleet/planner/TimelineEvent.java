package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.location.Location;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An immutable, atomic event recorded in a vehicle's scheduled timeline.
 */
public final class TimelineEvent {

    public enum EventType {
        EMPTY_DRIVE,
        PASSENGER_TRIP,
        CHARGING,
        IDLE
    }

    private final EventType eventType;
    private final Instant startTime;
    private final Instant endTime;
    private final Location startLocation;
    private final Location endLocation;
    private final double distanceKm;
    private final double energyDeltaKwh; // negative for consumption, positive for charging
    private final double startSoc;
    private final double endSoc;
    private final String referenceId; // tripId or sessionId
    private final String description;

    public TimelineEvent(EventType eventType, Instant startTime, Instant endTime,
                         Location startLocation, Location endLocation,
                         double distanceKm, double energyDeltaKwh,
                         double startSoc, double endSoc,
                         String referenceId, String description) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
        this.startLocation = Objects.requireNonNull(startLocation, "startLocation must not be null");
        this.endLocation = Objects.requireNonNull(endLocation, "endLocation must not be null");
        this.distanceKm = Math.max(0.0, distanceKm);
        this.energyDeltaKwh = energyDeltaKwh;
        this.startSoc = startSoc;
        this.endSoc = endSoc;
        this.referenceId = referenceId != null ? referenceId : "";
        this.description = description != null ? description : "";

        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException(String.format(
                    "endTime (%s) cannot be before startTime (%s)", endTime, startTime));
        }
    }

    public static TimelineEvent emptyDrive(Instant start, Instant end, Location from, Location to,
                                           double distanceKm, double energyKwh, double startSoc, double endSoc,
                                           String tripId) {
        return new TimelineEvent(EventType.EMPTY_DRIVE, start, end, from, to, distanceKm,
                -Math.abs(energyKwh), startSoc, endSoc, tripId, "Empty deadhead to pickup for " + tripId);
    }

    public static TimelineEvent passengerTrip(Instant start, Instant end, Location from, Location to,
                                              double distanceKm, double energyKwh, double startSoc, double endSoc,
                                              String tripId) {
        return new TimelineEvent(EventType.PASSENGER_TRIP, start, end, from, to, distanceKm,
                -Math.abs(energyKwh), startSoc, endSoc, tripId, "Passenger service for " + tripId);
    }

    public static TimelineEvent charging(Instant start, Instant end, Location hubLocation,
                                         double energyKwh, double startSoc, double endSoc,
                                         String sessionId, String hubId) {
        return new TimelineEvent(EventType.CHARGING, start, end, hubLocation, hubLocation, 0.0,
                Math.abs(energyKwh), startSoc, endSoc, sessionId, "Charging at " + hubId);
    }

    public EventType getEventType() { return eventType; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Duration getDuration() { return Duration.between(startTime, endTime); }
    public Location getStartLocation() { return startLocation; }
    public Location getEndLocation() { return endLocation; }
    public double getDistanceKm() { return distanceKm; }
    public double getEnergyDeltaKwh() { return energyDeltaKwh; }
    public double getStartSoc() { return startSoc; }
    public double getEndSoc() { return endSoc; }
    public String getReferenceId() { return referenceId; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("TimelineEvent[%s, %s to %s, soc: %.3f -> %.3f, energyDelta: %.2fkWh, ref=%s]",
                eventType, startTime, endTime, startSoc, endSoc, energyDeltaKwh, referenceId);
    }
}
