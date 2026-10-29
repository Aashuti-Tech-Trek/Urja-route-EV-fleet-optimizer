package com.moveinsync.evfleet.location;

import com.moveinsync.evfleet.exception.InvalidLocationException;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.util.Objects;

/**
 * Immutable value object representing a geographic point on Earth defined by latitude and longitude.
 *
 * <p>Distance calculations use the great-circle Haversine formula assuming a spherical Earth
 * with a mean volumetric radius of 6,371.0 km (IUGG mean radius).
 *
 * <p><strong>Important:</strong> The Haversine distance represents the idealized straight-line
 * (great-circle) geographic distance. It does not account for road topology, terrain elevation,
 * one-way systems, or real-world traffic restrictions, and must be scaled or replaced by road network
 * routing when determining physical travel duration and routing distance.
 */
public record Location(double latitude, double longitude) {

    /** Mean volumetric radius of the Earth in kilometres (WGS-84 / IUGG). */
    public static final double EARTH_RADIUS_KM = 6371.0;

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    /**
     * Compact constructor validating geographic coordinates.
     *
     * @param latitude  latitude in degrees, between -90.0 and 90.0 inclusive
     * @param longitude longitude in degrees, between -180.0 and 180.0 inclusive
     * @throws InvalidLocationException if coordinates are NaN, infinite, or out of range
     */
    public Location {
        DomainValidator.requireInRange(latitude, MIN_LATITUDE, MAX_LATITUDE, "Latitude",
                InvalidLocationException::new);
        DomainValidator.requireInRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "Longitude",
                InvalidLocationException::new);
    }

    /**
     * Calculates the great-circle distance between this location and another location
     * using the Haversine formula.
     *
     * @param other destination location, must not be null
     * @return great-circle distance in kilometres
     * @throws InvalidLocationException if other is null
     */
    public double distanceTo(Location other) {
        DomainValidator.requireNonNull(other, "Destination location", InvalidLocationException::new);

        if (this.equals(other)) {
            return 0.0;
        }

        double lat1Rad = Math.toRadians(this.latitude);
        double lon1Rad = Math.toRadians(this.longitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double lon2Rad = Math.toRadians(other.longitude);

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double sinHalfDeltaLat = Math.sin(deltaLat / 2.0);
        double sinHalfDeltaLon = Math.sin(deltaLon / 2.0);

        double a = (sinHalfDeltaLat * sinHalfDeltaLat)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * (sinHalfDeltaLon * sinHalfDeltaLon);

        // Clamp to [0, 1] to avoid domain errors in asin/sqrt due to numerical precision
        a = Math.min(1.0, Math.max(0.0, a));
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_KM * c;
    }

    @Override
    public String toString() {
        return String.format("Location(lat=%.6f, lon=%.6f)", latitude, longitude);
    }
}
