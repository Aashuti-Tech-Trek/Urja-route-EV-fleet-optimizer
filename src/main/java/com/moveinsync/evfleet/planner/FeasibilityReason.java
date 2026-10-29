package com.moveinsync.evfleet.planner;

/**
 * Stable reason codes identifying why a trip is infeasible for a specific vehicle.
 *
 * <p>Reason codes are stable identifiers suitable for machine-readable output, monitoring,
 * and traceability. The associated human-readable explanation is in {@link FeasibilityResult}.
 *
 * <p><strong>Important:</strong> A result may have only one primary reason code even if multiple
 * independent constraints fail simultaneously. The reported reason is the <em>first failing check
 * in evaluation order</em>. Future phases may extend this to a list of all failing reasons.
 */
public enum FeasibilityReason {

    /**
     * Check passed; vehicle can serve this trip (used only when status = FEASIBLE).
     */
    NONE,

    /**
     * Vehicle is not in AVAILABLE status or its availableFrom time is after the trip's
     * scheduled start minus empty-drive time.
     */
    VEHICLE_UNAVAILABLE,

    /**
     * The estimated empty travel time from the vehicle's current location to the trip pickup
     * causes the vehicle to arrive after the scheduled start time, and no charging or
     * repositioning can close the gap within the deadline window.
     */
    PICKUP_UNREACHABLE,

    /**
     * Even if the vehicle arrived at pickup on time, the trip's latest allowed finish time
     * cannot be met given the trip distance and the travel-time model.
     */
    DEADLINE_INFEASIBLE,

    /**
     * The vehicle does not have enough energy above the 15% safety reserve to complete the
     * empty drive to pickup, the assigned trip, or both.
     */
    INSUFFICIENT_ENERGY,

    /**
     * The vehicle's battery would drop below the 15% safety reserve floor after the
     * empty drive to pickup — before the trip even begins.
     */
    INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE,

    /**
     * The vehicle's battery would drop below the 15% safety reserve floor after completing
     * the assigned trip distance.
     */
    INSUFFICIENT_ENERGY_FOR_TRIP,

    /**
     * Charging is required before the trip can proceed, but no charger slot is available
     * within the required time window.
     */
    CHARGER_CAPACITY_UNAVAILABLE,

    /**
     * One or more inputs were null, blank, or violated domain invariants (e.g. negative distance).
     * This is a programming error, not a planning infeasibility.
     */
    INVALID_INPUT
}
