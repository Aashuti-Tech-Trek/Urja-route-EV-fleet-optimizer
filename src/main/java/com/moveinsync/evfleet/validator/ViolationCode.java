package com.moveinsync.evfleet.validator;

/**
 * Stable machine-readable violation codes emitted by the independent plan validator.
 */
public enum ViolationCode {

    /**
     * Vehicle State of Charge (SoC) dropped below the mandatory 15% reserve floor at an event.
     */
    BATTERY_RESERVE_VIOLATION,

    /**
     * Vehicle battery charged above 100% capacity or delivered energy exceeds physical capacity.
     */
    BATTERY_CAPACITY_EXCEEDED,

    /**
     * A requested passenger trip was completely omitted from the plan assignments.
     */
    TRIP_UNASSIGNED,

    /**
     * The same passenger trip was assigned more than once across EV or backup cab assignments.
     */
    DUPLICATE_TRIP_ASSIGNMENT,

    /**
     * The assigned EV cannot arrive at pickup before or at the scheduled trip start time,
     * or vehicle availability starts after the required departure.
     */
    PICKUP_TIME_INFEASIBLE,

    /**
     * A passenger trip finishes after its latest allowed finish time.
     */
    TRIP_DEADLINE_VIOLATION,

    /**
     * Two or more consecutive events on the same vehicle overlap in time.
     */
    VEHICLE_TIMELINE_OVERLAP,

    /**
     * Active charging sessions at a charging hub exceed the hub's physical charger count.
     */
    CHARGER_CAPACITY_EXCEEDED,

    /**
     * A charging session has invalid interval bounds (end <= start), negative energy, or inverted SoC.
     */
    CHARGING_SESSION_INVALID,

    /**
     * A charging session is scheduled concurrently with a passenger trip or repositioning drive.
     */
    CHARGING_DURING_TRIP,

    /**
     * Physical energy consumption or charging energy delta does not reconcile with starting and ending SoC.
     */
    ENERGY_ACCOUNTING_MISMATCH,

    /**
     * Recorded charging session cost or total plan cost does not match the applicable tariff schedule.
     */
    CHARGING_COST_MISMATCH,

    /**
     * Required plan components or input collections are null or corrupted, preventing full validation.
     */
    MISSING_VALIDATION_DATA,

    /**
     * A plan assignment, timeline, or session references an unrecognized vehicle, trip, or hub ID.
     */
    UNKNOWN_ENTITY_REFERENCED,

    /**
     * Consecutive events on a vehicle have spatial discontinuity without an intervening movement event.
     */
    SPATIAL_DISCONTINUITY,

    /**
     * High-level plan summary metrics (e.g. backup count, session count) disagree with individual assignments.
     */
    METRIC_INCONSISTENCY
}
