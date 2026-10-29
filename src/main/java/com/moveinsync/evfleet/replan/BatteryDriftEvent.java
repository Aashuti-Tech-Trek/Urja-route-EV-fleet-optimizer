package com.moveinsync.evfleet.replan;

import com.moveinsync.evfleet.domain.Vehicle;

import java.time.Instant;
import java.util.Objects;

/**
 * Event representing real-world telemetry drift in an electric vehicle's State of Charge (SoC).
 */
public final class BatteryDriftEvent {

    private final String eventId;
    private final String vehicleId;
    private final double actualSoc;
    private final Instant eventTimestamp;

    public BatteryDriftEvent(String eventId, String vehicleId, double actualSoc, Instant eventTimestamp) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        this.actualSoc = actualSoc;
        this.eventTimestamp = Objects.requireNonNull(eventTimestamp, "eventTimestamp must not be null");

        if (Double.isNaN(actualSoc) || actualSoc < 0.0 || actualSoc > 1.0) {
            throw new IllegalArgumentException("Actual SoC must be in [0.0, 1.0], but got: " + actualSoc);
        }
    }

    public String getEventId() {
        return eventId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public double getActualSoc() {
        return actualSoc;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    @Override
    public String toString() {
        return String.format("BatteryDriftEvent[id=%s, vehicle=%s, actualSoc=%.4f, time=%s]",
                eventId, vehicleId, actualSoc, eventTimestamp);
    }
}
