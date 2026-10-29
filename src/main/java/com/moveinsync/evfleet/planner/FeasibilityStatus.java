package com.moveinsync.evfleet.planner;

/**
 * Top-level result of a trip feasibility check.
 */
public enum FeasibilityStatus {
    /** The vehicle can serve this trip from its current planning state. */
    FEASIBLE,
    /** One or more hard constraints prevent this vehicle from serving this trip. */
    INFEASIBLE
}
