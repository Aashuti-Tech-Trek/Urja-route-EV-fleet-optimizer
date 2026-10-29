package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidVehicleException;
import com.moveinsync.evfleet.location.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTest {

    private final Location baseLocation = new Location(12.9279, 77.6826);
    private final Instant now = Instant.parse("2026-10-29T10:00:00Z");

    @Test
    @DisplayName("Valid vehicle creation with specifications and state")
    void shouldCreateValidVehicle() {
        Vehicle vehicle = Vehicle.create(
                "VEH-001",
                "TENANT-BLR",
                60.0,
                0.15,
                0.85,
                baseLocation,
                now
        );

        assertThat(vehicle.getVehicleId()).isEqualTo("VEH-001");
        assertThat(vehicle.getTenantId()).isEqualTo("TENANT-BLR");
        assertThat(vehicle.getBatteryCapacityKwh()).isEqualTo(60.0);
        assertThat(vehicle.getBaseConsumptionKwhPerKm()).isEqualTo(0.15);
        assertThat(vehicle.getStateOfCharge()).isEqualTo(0.85);
        assertThat(vehicle.getCurrentLocation()).isEqualTo(baseLocation);
        assertThat(vehicle.getAvailableFrom()).isEqualTo(now);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Reject blank or empty vehicle ID and tenant ID")
    void shouldRejectBlankIdentifiers(String blankId) {
        assertThatThrownBy(() -> Vehicle.create(blankId, "TENANT-1", 60.0, 0.15, 0.85, baseLocation, now))
                .isInstanceOf(InvalidVehicleException.class)
                .hasMessageContaining("Vehicle ID");

        assertThatThrownBy(() -> Vehicle.create("VEH-1", blankId, 60.0, 0.15, 0.85, baseLocation, now))
                .isInstanceOf(InvalidVehicleException.class)
                .hasMessageContaining("Tenant ID");
    }

    @Test
    @DisplayName("Reject null vehicle ID and tenant ID")
    void shouldRejectNullIdentifiers() {
        assertThatThrownBy(() -> Vehicle.create(null, "TENANT-1", 60.0, 0.15, 0.85, baseLocation, now))
                .isInstanceOf(InvalidVehicleException.class);

        assertThatThrownBy(() -> Vehicle.create("VEH-1", null, 60.0, 0.15, 0.85, baseLocation, now))
                .isInstanceOf(InvalidVehicleException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -10.0, -0.001, Double.NaN})
    @DisplayName("Reject non-positive battery capacity and consumption")
    void shouldRejectNonPositiveSpecValues(double invalidValue) {
        assertThatThrownBy(() -> new VehicleSpecification(invalidValue, 0.15))
                .isInstanceOf(InvalidVehicleException.class)
                .hasMessageContaining("Battery capacity");

        assertThatThrownBy(() -> new VehicleSpecification(60.0, invalidValue))
                .isInstanceOf(InvalidVehicleException.class)
                .hasMessageContaining("Base consumption");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, -1.0, 2.0, Double.NaN})
    @DisplayName("Reject state of charge outside [0.0, 1.0]")
    void shouldRejectOutOfRangeSoc(double invalidSoc) {
        assertThatThrownBy(() -> Vehicle.create("VEH-1", "TENANT-1", 60.0, 0.15, invalidSoc, baseLocation, now))
                .isInstanceOf(InvalidVehicleException.class)
                .hasMessageContaining("State of charge");
    }

    @Test
    @DisplayName("Vehicle state transitions return new immutable copies")
    void shouldTransitionStateImmutably() {
        Vehicle original = Vehicle.create("VEH-1", "TENANT-1", 60.0, 0.15, 0.90, baseLocation, now);

        Location newLocation = new Location(13.1986, 77.7066);
        Instant nextHour = now.plusSeconds(3600);
        Vehicle updated = original.withUpdatedSocAndLocation(0.65, newLocation, nextHour);

        // Original remains unchanged
        assertThat(original.getStateOfCharge()).isEqualTo(0.90);
        assertThat(original.getCurrentLocation()).isEqualTo(baseLocation);

        // Updated has new state
        assertThat(updated.getStateOfCharge()).isEqualTo(0.65);
        assertThat(updated.getCurrentLocation()).isEqualTo(newLocation);
        assertThat(updated.getAvailableFrom()).isEqualTo(nextHour);
        assertThat(updated.getVehicleId()).isEqualTo(original.getVehicleId());
    }
}
