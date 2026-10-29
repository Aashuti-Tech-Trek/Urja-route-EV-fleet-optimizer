package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.ChargingSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Tracks concurrent charger occupancy at charging hubs to prevent oversubscription.
 *
 * <p>Enforces the hard invariant that concurrent charging sessions at any hub
 * must never exceed {@code hub.getChargerCount()} at any instant.
 */
public final class ChargerOccupancyTracker {

    private final Map<String, List<ChargingSession>> sessionsByHub;

    public ChargerOccupancyTracker() {
        this.sessionsByHub = new HashMap<>();
    }

    private ChargerOccupancyTracker(Map<String, List<ChargingSession>> sessionsByHub) {
        this.sessionsByHub = new HashMap<>();
        for (Map.Entry<String, List<ChargingSession>> entry : sessionsByHub.entrySet()) {
            this.sessionsByHub.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    /**
     * Creates an isolated snapshot copy for speculative planning.
     */
    public ChargerOccupancyTracker copy() {
        return new ChargerOccupancyTracker(this.sessionsByHub);
    }

    /**
     * Checks if a new charging session can be scheduled at the hub without exceeding chargerCount.
     *
     * @param hubId the hub ID
     * @param start session start time
     * @param end session end time
     * @param chargerCount total number of physical chargers available at the hub
     * @return true if a charger is free throughout [start, end), false otherwise
     */
    public boolean hasCapacity(String hubId, Instant start, Instant end, int chargerCount) {
        Objects.requireNonNull(hubId, "hubId must not be null");
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");

        if (!end.isAfter(start)) {
            return false;
        }

        if (chargerCount <= 0) {
            return false;
        }

        List<ChargingSession> hubSessions = sessionsByHub.get(hubId);
        if (hubSessions == null || hubSessions.isEmpty()) {
            return true;
        }

        // Filter sessions that overlap with [start, end)
        List<ChargingSession> overlapping = new ArrayList<>();
        for (ChargingSession s : hubSessions) {
            if (s.getStartTime().isBefore(end) && s.getEndTime().isAfter(start)) {
                overlapping.add(s);
            }
        }

        if (overlapping.isEmpty()) {
            return true;
        }

        // Collect all distinct timestamps within [start, end]
        TreeSet<Instant> timestamps = new TreeSet<>();
        timestamps.add(start);
        timestamps.add(end);
        for (ChargingSession s : overlapping) {
            if (s.getStartTime().isAfter(start) && s.getStartTime().isBefore(end)) {
                timestamps.add(s.getStartTime());
            }
            if (s.getEndTime().isAfter(start) && s.getEndTime().isBefore(end)) {
                timestamps.add(s.getEndTime());
            }
        }

        // Check concurrency across all sub-intervals between adjacent timestamps
        List<Instant> sortedPoints = new ArrayList<>(timestamps);
        for (int i = 0; i < sortedPoints.size() - 1; i++) {
            Instant subStart = sortedPoints.get(i);
            Instant subEnd = sortedPoints.get(i + 1);
            // Midpoint of interval
            long midEpochMilli = (subStart.toEpochMilli() + subEnd.toEpochMilli()) / 2;
            Instant midPoint = Instant.ofEpochMilli(midEpochMilli);

            int activeCount = 0;
            for (ChargingSession s : overlapping) {
                if (!midPoint.isBefore(s.getStartTime()) && midPoint.isBefore(s.getEndTime())) {
                    activeCount++;
                }
            }

            // With the proposed session, total concurrency is activeCount + 1
            if (activeCount + 1 > chargerCount) {
                return false;
            }
        }

        return true;
    }

    /**
     * Registers a new charging session.
     *
     * @param session the session to record
     */
    public void recordSession(ChargingSession session) {
        Objects.requireNonNull(session, "session must not be null");
        sessionsByHub.computeIfAbsent(session.getHubId(), k -> new ArrayList<>()).add(session);
    }

    public List<ChargingSession> getSessionsForHub(String hubId) {
        List<ChargingSession> list = sessionsByHub.get(hubId);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    public List<ChargingSession> getAllSessions() {
        List<ChargingSession> all = new ArrayList<>();
        for (List<ChargingSession> list : sessionsByHub.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableList(all);
    }
}
