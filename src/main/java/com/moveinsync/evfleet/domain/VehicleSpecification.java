package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidVehicleException;
import com.moveinsync.evfleet.validation.DomainValidator;

/**
 * Immutable physical specification of an electric vehicle.
 *
 * @param batteryCapacityKwh       total battery capacity in kWh, strictly positive
 * @param baseConsumptionKwhPerKm base vehicle energy consumption in kWh/km, strictly positive
 */
public record VehicleSpecification(double batteryCapacityKwh, double baseConsumptionKwhPerKm) {

    public VehicleSpecification {
        DomainValidator.requirePositive(batteryCapacityKwh, "Battery capacity (kWh)",
                InvalidVehicleException::new);
        DomainValidator.requirePositive(baseConsumptionKwhPerKm, "Base consumption (kWh/km)",
                InvalidVehicleException::new);
    }
}
