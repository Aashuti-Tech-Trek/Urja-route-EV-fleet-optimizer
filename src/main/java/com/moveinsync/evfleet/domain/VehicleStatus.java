package com.moveinsync.evfleet.domain;

/**
 * Operational state of an electric vehicle.
 */
public enum VehicleStatus {
    AVAILABLE,
    ON_TRIP,
    CHARGING,
    MAINTENANCE,
    OFFLINE
}
