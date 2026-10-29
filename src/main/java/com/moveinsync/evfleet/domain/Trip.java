package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidTripException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an employee commute trip request.
 *
 * <p><strong>Engineering Boundary:</strong>
 * Fixed travel-time estimation formulas are intentionally avoided here.
 * Trip duration and feasibility will be resolved by an external travel-time model
 * when assignment planning is introduced in subsequent phases.
 */
public class Trip {

    private final String tripId;
    private final String tenantId;
    private final Location startLocation;
    private final Location destination;
    private final double distanceKm;
    private final Instant scheduledStartTime;
    private final Instant latestAllowedFinishTime;

    public Trip(String tripId, String tenantId, Location startLocation, Location destination,
                double distanceKm, Instant scheduledStartTime, Instant latestAllowedFinishTime) {
        this.tripId = DomainValidator.requireNonBlank(tripId, "Trip ID", InvalidTripException::new);
        this.tenantId = DomainValidator.requireNonBlank(tenantId, "Tenant ID", InvalidTripException::new);
        this.startLocation = DomainValidator.requireNonNull(startLocation, "Start location", InvalidTripException::new);
        this.destination = DomainValidator.requireNonNull(destination, "Destination", InvalidTripException::new);
        this.distanceKm = DomainValidator.requirePositive(distanceKm, "Distance (km)", InvalidTripException::new);
        this.scheduledStartTime = DomainValidator.requireNonNull(scheduledStartTime, "Scheduled start time", InvalidTripException::new);
        this.latestAllowedFinishTime = DomainValidator.requireNonNull(latestAllowedFinishTime, "Latest allowed finish time", InvalidTripException::new);

        if (!latestAllowedFinishTime.isAfter(scheduledStartTime)) {
            throw new InvalidTripException(String.format(
                    "Latest allowed finish time (%s) must be strictly after scheduled start time (%s)",
                    latestAllowedFinishTime, scheduledStartTime));
        }
    }

    public String getTripId() {
        return tripId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public Location getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public Instant getScheduledStartTime() {
        return scheduledStartTime;
    }

    public Instant getLatestAllowedFinishTime() {
        return latestAllowedFinishTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trip trip)) return false;
        return Objects.equals(tripId, trip.tripId) && Objects.equals(tenantId, trip.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId, tenantId);
    }

    @Override
    public String toString() {
        return String.format("Trip[id=%s, tenant=%s, dist=%.2fkm, start=%s, finishBy=%s]",
                tripId, tenantId, distanceKm, scheduledStartTime, latestAllowedFinishTime);
    }
}
