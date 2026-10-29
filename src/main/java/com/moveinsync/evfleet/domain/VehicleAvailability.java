package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidVehicleException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.Instant;

/**
 * Representation of vehicle availability for dispatch or charging.
 *
 * @param availableFrom       instant from which the vehicle is free to begin travel/charging
 * @param availableAtLocation physical location where the vehicle becomes available
 * @param status              current operational status
 */
public record VehicleAvailability(Instant availableFrom, Location availableAtLocation, VehicleStatus status) {

    public VehicleAvailability {
        DomainValidator.requireNonNull(availableFrom, "Available from instant", InvalidVehicleException::new);
        DomainValidator.requireNonNull(availableAtLocation, "Available at location", InvalidVehicleException::new);
        DomainValidator.requireNonNull(status, "Vehicle status", InvalidVehicleException::new);
    }

    public static VehicleAvailability availableAt(Instant availableFrom, Location location) {
        return new VehicleAvailability(availableFrom, location, VehicleStatus.AVAILABLE);
    }
}
