package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidVehicleException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root representing an electric cab in the MoveInSync fleet.
 *
 * <p>Maintains a clean architectural separation between immutable physical vehicle specifications
 * ({@link VehicleSpecification}) and dynamic operational runtime state ({@link VehicleState}).
 */
public class Vehicle {

    private final String vehicleId;
    private final String tenantId;
    private final VehicleSpecification specification;
    private final VehicleState state;

    public Vehicle(String vehicleId, String tenantId, VehicleSpecification specification, VehicleState state) {
        this.vehicleId = DomainValidator.requireNonBlank(vehicleId, "Vehicle ID", InvalidVehicleException::new);
        this.tenantId = DomainValidator.requireNonBlank(tenantId, "Tenant ID", InvalidVehicleException::new);
        this.specification = DomainValidator.requireNonNull(specification, "Vehicle specification", InvalidVehicleException::new);
        this.state = DomainValidator.requireNonNull(state, "Vehicle state", InvalidVehicleException::new);
    }

    /**
     * Convenience factory constructor for initializing a vehicle.
     */
    public static Vehicle create(String vehicleId, String tenantId, double batteryCapacityKwh,
                                double baseConsumptionKwhPerKm, double stateOfCharge,
                                Location currentLocation, Instant availableFrom) {
        VehicleSpecification spec = new VehicleSpecification(batteryCapacityKwh, baseConsumptionKwhPerKm);
        VehicleState st = VehicleState.initial(currentLocation, stateOfCharge, availableFrom);
        return new Vehicle(vehicleId, tenantId, spec, st);
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public VehicleSpecification getSpecification() {
        return specification;
    }

    public VehicleState getState() {
        return state;
    }

    // Convenience delegates
    public double getBatteryCapacityKwh() {
        return specification.batteryCapacityKwh();
    }

    public double getBaseConsumptionKwhPerKm() {
        return specification.baseConsumptionKwhPerKm();
    }

    public Location getCurrentLocation() {
        return state.currentLocation();
    }

    public double getStateOfCharge() {
        return state.stateOfCharge();
    }

    public Instant getAvailableFrom() {
        return state.availability().availableFrom();
    }

    public VehicleStatus getStatus() {
        return state.availability().status();
    }

    /**
     * Returns a new Vehicle instance with updated runtime state.
     */
    public Vehicle withUpdatedState(VehicleState newState) {
        return new Vehicle(this.vehicleId, this.tenantId, this.specification, newState);
    }

    /**
     * Returns a new Vehicle instance with updated SoC and location.
     */
    public Vehicle withUpdatedSocAndLocation(double newSoc, Location newLocation, Instant newAvailableFrom) {
        VehicleState updatedState = VehicleState.initial(newLocation, newSoc, newAvailableFrom);
        return withUpdatedState(updatedState);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle other)) return false;
        return Objects.equals(vehicleId, other.vehicleId) && Objects.equals(tenantId, other.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vehicleId, tenantId);
    }

    @Override
    public String toString() {
        return String.format("Vehicle[id=%s, tenant=%s, cap=%.1fkWh, soc=%.1f%%, loc=%s]",
                vehicleId, tenantId, getBatteryCapacityKwh(), getStateOfCharge() * 100.0, getCurrentLocation());
    }
}
