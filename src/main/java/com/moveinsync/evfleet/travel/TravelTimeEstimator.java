package com.moveinsync.evfleet.travel;

import com.moveinsync.evfleet.location.Location;

import java.time.Duration;

/**
 * Strategy interface for estimating travel time between two geographic locations.
 *
 * <p>All implementations must be stateless, thread-safe, and produce deterministic results
 * for identical inputs. The results are estimates unless backed by live road-routing data.
 *
 * <p><strong>Contract for implementors:</strong>
 * <ul>
 *   <li>Must not return a negative duration.</li>
 *   <li>Must return {@link Duration#ZERO} when {@code from} and {@code to} are the same location.</li>
 *   <li>Must throw {@link com.moveinsync.evfleet.exception.InvalidLocationException} if either
 *       location is null.</li>
 *   <li>Must document the source of truth (Haversine, OSRM, Google Maps, etc.) and all assumptions
 *       that affect the result.</li>
 * </ul>
 *
 * <p><strong>Known limitation:</strong> Implementations based on straight-line distance do not
 * account for road topology, one-way roads, or traffic signals. They are labelled as
 * approximations and should be replaced with road-routing API results in production.
 */
public interface TravelTimeEstimator {

    /**
     * Estimates the travel time from {@code from} to {@code to}.
     *
     * @param from source location; must not be null
     * @param to   destination location; must not be null
     * @return estimated travel duration, never negative, zero iff {@code from.equals(to)}
     */
    Duration estimateTravelTime(Location from, Location to);

    /**
     * Estimates the approximate road distance in kilometres between two locations.
     *
     * <p>This is the road-distance estimate (Haversine × road-distance factor) used for
     * travel-time calculation, NOT the raw Haversine distance.
     *
     * @param from source location; must not be null
     * @param to   destination location; must not be null
     * @return estimated road distance in km, non-negative
     */
    double estimateRoadDistanceKm(Location from, Location to);
}
