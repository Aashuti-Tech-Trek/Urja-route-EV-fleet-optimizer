package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidVehicleException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.Instant;

/**
 * Mutable runtime state of an electric vehicle.
 *
 * @param currentLocation current geographic position
 * @param stateOfCharge   current battery state-of-charge in [0.0, 1.0]
 * @param availability    availability window and status
 */
public record VehicleState(Location currentLocation, double stateOfCharge, VehicleAvailability availability) {

    public VehicleState {
        DomainValidator.requireNonNull(currentLocation, "Current location", InvalidVehicleException::new);
        DomainValidator.requireInRange(stateOfCharge, 0.0, 1.0, "State of charge", InvalidVehicleException::new);
        DomainValidator.requireNonNull(availability, "Vehicle availability", InvalidVehicleException::new);
    }

    public static VehicleState initial(Location location, double initialSoc, Instant availableFrom) {
        return new VehicleState(
                location,
                initialSoc,
                VehicleAvailability.availableAt(availableFrom, location)
        );
    }
}
