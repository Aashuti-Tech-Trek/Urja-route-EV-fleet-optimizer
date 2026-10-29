package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;

import java.util.List;
import java.util.Objects;

/**
 * Immutable input to the planning engine for a single planning run.
 *
 * <p>All collections are stored as unmodifiable views. The planner must not mutate vehicles or trips.
 */
public final class PlanningInput {

    private final String planningRunId;
    private final List<Vehicle> vehicles;
    private final List<Trip> trips;
    private final List<ChargerHub> hubs;

    public PlanningInput(String planningRunId, List<Vehicle> vehicles, List<Trip> trips) {
        this(planningRunId, vehicles, trips, List.of());
    }

    public PlanningInput(String planningRunId, List<Vehicle> vehicles, List<Trip> trips, List<ChargerHub> hubs) {
        this.planningRunId = Objects.requireNonNull(planningRunId, "planningRunId must not be null");
        this.vehicles = List.copyOf(Objects.requireNonNull(vehicles, "vehicles must not be null"));
        this.trips = List.copyOf(Objects.requireNonNull(trips, "trips must not be null"));
        this.hubs = hubs != null ? List.copyOf(hubs) : List.of();
    }

    public String getPlanningRunId() { return planningRunId; }
    public List<Vehicle> getVehicles() { return vehicles; }
    public List<Trip> getTrips() { return trips; }
    public List<ChargerHub> getHubs() { return hubs; }

    @Override
    public String toString() {
        return String.format("PlanningInput[runId=%s, vehicles=%d, trips=%d, hubs=%d]",
                planningRunId, vehicles.size(), trips.size(), hubs.size());
    }
}
