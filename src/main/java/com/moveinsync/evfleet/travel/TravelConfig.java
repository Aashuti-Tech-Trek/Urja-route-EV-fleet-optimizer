package com.moveinsync.evfleet.travel;

import com.moveinsync.evfleet.exception.InvalidDomainException;
import com.moveinsync.evfleet.validation.DomainValidator;

/**
 * Immutable configuration for travel-time estimation heuristics.
 *
 * <p><strong>Assumptions:</strong>
 * <ul>
 *   <li>{@code roadDistanceFactor} scales great-circle Haversine distance to approximate road
 *       distance. A value of 1.3 means actual road routing is assumed to be 30% longer than the
 *       straight-line distance. This is a modelling assumption, not a measured value.</li>
 *   <li>{@code averageSpeedKmh} is the assumed average driving speed in km/h inclusive of traffic
 *       and urban stop-and-go conditions. The baseline value of 25 km/h is representative of
 *       peak-evening urban traffic for Bengaluru/Mumbai; it is not derived from real road data.</li>
 * </ul>
 *
 * <p>Both values must be replaced with road-routing API results in production deployments.
 *
 * <p><strong>Validity constraints:</strong>
 * <ul>
 *   <li>roadDistanceFactor must be in [1.0, 5.0]: values below 1.0 would imply road distance is
 *       shorter than straight-line distance (physically impossible); values above 5.0 are
 *       unrealistically large for any urban scenario.</li>
 *   <li>averageSpeedKmh must be in (0.0, 200.0]: 200 km/h represents a hard upper cap for urban
 *       cab operation.</li>
 * </ul>
 */
public final class TravelConfig {

    /** Default road-distance factor: road path is assumed to be 1.3× the Haversine distance. */
    public static final double DEFAULT_ROAD_DISTANCE_FACTOR = 1.3;

    /** Default average speed in km/h for urban evening-shift traffic (modelling assumption). */
    public static final double DEFAULT_AVERAGE_SPEED_KMH = 25.0;

    public static final double MIN_ROAD_DISTANCE_FACTOR = 1.0;
    public static final double MAX_ROAD_DISTANCE_FACTOR = 5.0;
    public static final double MIN_SPEED_KMH           = 0.01; // strictly positive
    public static final double MAX_SPEED_KMH           = 200.0;

    private final double roadDistanceFactor;
    private final double averageSpeedKmh;

    /**
     * Creates a TravelConfig with explicit values.
     *
     * @param roadDistanceFactor scale factor applied to Haversine distance to approximate road
     *                           distance; must be in [1.0, 5.0]
     * @param averageSpeedKmh    assumed average driving speed in km/h; must be in (0.0, 200.0]
     * @throws InvalidDomainException if any parameter is out of range or non-finite
     */
    public TravelConfig(double roadDistanceFactor, double averageSpeedKmh) {
        this.roadDistanceFactor = DomainValidator.requireInRange(
                roadDistanceFactor, MIN_ROAD_DISTANCE_FACTOR, MAX_ROAD_DISTANCE_FACTOR,
                "Road distance factor");
        this.averageSpeedKmh = DomainValidator.requireInRange(
                averageSpeedKmh, MIN_SPEED_KMH, MAX_SPEED_KMH,
                "Average speed (km/h)");
    }

    /**
     * Returns a TravelConfig with the documented baseline defaults:
     * roadDistanceFactor = 1.3, averageSpeedKmh = 25 km/h.
     */
    public static TravelConfig defaultConfig() {
        return new TravelConfig(DEFAULT_ROAD_DISTANCE_FACTOR, DEFAULT_AVERAGE_SPEED_KMH);
    }

    /**
     * Returns the road-distance factor (straight-line to estimated road-distance multiplier).
     */
    public double getRoadDistanceFactor() {
        return roadDistanceFactor;
    }

    /**
     * Returns the assumed average driving speed in km/h.
     */
    public double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    @Override
    public String toString() {
        return String.format("TravelConfig[roadFactor=%.2f, avgSpeed=%.1f km/h]",
                roadDistanceFactor, averageSpeedKmh);
    }
}
