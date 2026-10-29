package com.moveinsync.evfleet.travel;

import com.moveinsync.evfleet.exception.InvalidLocationException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily-populated, sparse distance cache for repeated location-pair queries during a planning run.
 *
 * <p><strong>Identity model:</strong> Two locations are considered the same cache key when their
 * latitude and longitude are exactly equal (Java {@code double} equality). This matches the
 * {@link Location} record's natural equality semantics (record canonical equals).
 *
 * <p><strong>Cache key:</strong> An ordered {@link LocationPair} (from, to) where (A→B) and (B→A)
 * are stored as separate entries. Road travel is asymmetric in general (one-way roads, traffic
 * direction), so symmetric collapsing would be incorrect for future road-routing implementations.
 *
 * <p><strong>Memory model:</strong> The cache is a {@link ConcurrentHashMap} holding one
 * {@code double} per ordered pair. For N distinct locations, the worst-case entry count is N×(N-1).
 * At 300 vehicles + 40 hub locations ≈ 340 distinct locations, worst case ≈ 115,260 entries,
 * each holding two {@code Location} references and one {@code double} — roughly 3-4 MB.
 * This is acceptable for a single planning run. The cache is scoped to the planning run and is
 * not shared or persisted across runs.
 *
 * <p><strong>Invalidation:</strong> The cache does not support partial invalidation. If location
 * data changes, create a new {@code DistanceMatrix} instance. Stale data cannot occur within
 * a single planning run assuming locations are immutable (they are records).
 *
 * <p><strong>Complexity:</strong> Lookup and insertion are O(1) amortised (hash map). First access
 * for a pair triggers one Haversine computation and one {@code roadDistanceFactor} multiply.
 */
public final class DistanceMatrix {

    private final TravelTimeEstimator estimator;
    private final ConcurrentHashMap<LocationPair, Double> cache;

    /**
     * Creates a DistanceMatrix backed by the given estimator.
     *
     * @param estimator the travel-time estimator supplying road-distance values; must not be null
     */
    public DistanceMatrix(TravelTimeEstimator estimator) {
        this.estimator = DomainValidator.requireNonNull(estimator, "TravelTimeEstimator");
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Returns the estimated road distance in kilometres between {@code from} and {@code to}.
     * The value is computed once and cached for subsequent calls with the same ordered pair.
     *
     * @param from source location; must not be null
     * @param to   destination location; must not be null
     * @return estimated road distance in km, non-negative
     */
    public double getRoadDistanceKm(Location from, Location to) {
        DomainValidator.requireNonNull(from, "Source location", InvalidLocationException::new);
        DomainValidator.requireNonNull(to, "Destination location", InvalidLocationException::new);

        LocationPair key = new LocationPair(from, to);
        return cache.computeIfAbsent(key, k -> estimator.estimateRoadDistanceKm(k.from(), k.to()));
    }

    /**
     * Returns the number of cached distance entries.
     * Useful for monitoring memory usage during a planning run.
     */
    public int cacheSize() {
        return cache.size();
    }

    /**
     * Clears all cached entries. Use when starting a new planning run to avoid stale data
     * if the underlying location set has changed.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Immutable ordered pair of locations used as the cache key.
     * (from → to) and (to → from) are treated as distinct keys.
     */
    public record LocationPair(Location from, Location to) {

        public LocationPair {
            Objects.requireNonNull(from, "from location must not be null");
            Objects.requireNonNull(to, "to location must not be null");
        }
    }
}
