package com.moveinsync.evfleet.planner;

/**
 * Interface contract for EV fleet trip assignment and charging optimization engines.
 */
public interface FleetPlanner {

    /**
     * Executes the planning engine on the provided input scenario.
     *
     * @param input immutable planning scenario containing vehicles, trips, and charger hubs
     * @return authoritative, inspectable FleetPlanResult
     */
    FleetPlanResult plan(PlanningInput input);
}
