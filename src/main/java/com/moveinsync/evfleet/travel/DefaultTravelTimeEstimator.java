package com.moveinsync.evfleet.travel;

import com.moveinsync.evfleet.exception.InvalidLocationException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.Duration;

/**
 * Default implementation of {@link TravelTimeEstimator} using Haversine great-circle distance
 * scaled by a road-distance factor and divided by a configured average speed.
 *
 * <p><strong>Source of truth:</strong> The trip's travel distance is estimated from the
 * Haversine distance between the two given locations, scaled by the configured
 * {@code roadDistanceFactor}. The trip's {@code distanceKm} field (from the domain object)
 * is <em>not</em> used here; this estimator operates solely on geographic coordinates.
 *
 * <p><strong>Assumptions:</strong>
 * <ol>
 *   <li>Road distance ≈ Haversine distance × {@code roadDistanceFactor} (default 1.3).</li>
 *   <li>Travel time ≈ road distance ÷ average speed (default 25 km/h).</li>
 *   <li>Result is rounded up to the nearest whole second to be conservative (ensures vehicles
 *       do not optimistically arrive earlier than reality would allow).</li>
 *   <li>These are modelling assumptions; actual road conditions are not considered.</li>
 * </ol>
 *
 * <p><strong>Limitations:</strong>
 * <ul>
 *   <li>Does not account for road topology, traffic signals, or one-way restrictions.</li>
 *   <li>Does not use the trip's declared {@code distanceKm} field as input.</li>
 *   <li>Replace with OSRM or Google Maps Distance Matrix API for production accuracy.</li>
 * </ul>
 *
 * <p>This class is thread-safe and stateless beyond its immutable configuration.
 */
public final class DefaultTravelTimeEstimator implements TravelTimeEstimator {

    private final TravelConfig config;

    /**
     * Creates an estimator with the provided travel configuration.
     *
     * @param config immutable travel configuration; must not be null
     */
    public DefaultTravelTimeEstimator(TravelConfig config) {
        this.config = DomainValidator.requireNonNull(config, "Travel configuration");
    }

    /**
     * Creates an estimator using the documented baseline defaults
     * (roadDistanceFactor=1.3, averageSpeedKmh=25).
     */
    public static DefaultTravelTimeEstimator withDefaults() {
        return new DefaultTravelTimeEstimator(TravelConfig.defaultConfig());
    }

    @Override
    public Duration estimateTravelTime(Location from, Location to) {
        DomainValidator.requireNonNull(from, "Source location", InvalidLocationException::new);
        DomainValidator.requireNonNull(to, "Destination location", InvalidLocationException::new);

        double roadDistanceKm = estimateRoadDistanceKm(from, to);
        if (roadDistanceKm == 0.0) {
            return Duration.ZERO;
        }

        // Time (hours) = distance / speed; convert to seconds and round up conservatively.
        double timeHours = roadDistanceKm / config.getAverageSpeedKmh();
        long seconds = (long) Math.ceil(timeHours * 3600.0);
        return Duration.ofSeconds(seconds);
    }

    @Override
    public double estimateRoadDistanceKm(Location from, Location to) {
        DomainValidator.requireNonNull(from, "Source location", InvalidLocationException::new);
        DomainValidator.requireNonNull(to, "Destination location", InvalidLocationException::new);

        double haversineKm = from.distanceTo(to);
        return haversineKm * config.getRoadDistanceFactor();
    }

    /**
     * Returns the travel configuration used by this estimator.
     */
    public TravelConfig getConfig() {
        return config;
    }
}
