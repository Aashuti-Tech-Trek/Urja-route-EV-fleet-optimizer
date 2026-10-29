package com.moveinsync.evfleet.explain;

/**
 * Structured outcome code for an alternative vehicle evaluated (or not evaluated) for a trip.
 *
 * <p>Codes are strictly derived from actual feasibility-check results and planner bookkeeping.
 * They are never fabricated.
 */
public enum AlternativeOutcome {
    /** Vehicle was not included in the heuristic candidate evaluation (e.g., diagnostic limit). */
    NOT_EVALUATED,
    /** Vehicle is feasible but was outranked on the heuristic comparison criteria. */
    FEASIBLE_BUT_NOT_SELECTED,
    /** Vehicle not in AVAILABLE status or available-from time misses the trip window. */
    VEHICLE_UNAVAILABLE,
    /** Empty pickup travel exceeds the window before the scheduled start. */
    PICKUP_TRAVEL_INFEASIBLE,
    /** Trip cannot be completed before its latest allowed finish time. */
    TRIP_DEADLINE_MISSED,
    /** Insufficient battery above the 15% reserve for the empty drive or passenger trip. */
    INSUFFICIENT_BATTERY,
    /** Charging required but no hub/slot is available in the required time window. */
    CHARGING_UNAVAILABLE,
    /** Infeasible for another constraint; see detail for specifics. */
    OTHER_CONSTRAINT
}
