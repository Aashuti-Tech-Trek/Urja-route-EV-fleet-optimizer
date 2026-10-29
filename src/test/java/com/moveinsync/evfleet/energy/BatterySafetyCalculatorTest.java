package com.moveinsync.evfleet.energy;

import com.moveinsync.evfleet.exception.InvalidEnergyCalculationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class BatterySafetyCalculatorTest {

    private static final double CAPACITY = 60.0; // 60 kWh EV battery (e.g. Tata Nexon EV / MG ZS EV)

    @Test
    @DisplayName("Minimum reserve energy is exactly 15% of battery capacity")
    void shouldCalculateExact15PercentReserveEnergy() {
        double reserve = BatterySafetyCalculator.calculateMinimumReserveEnergy(CAPACITY);
        // 60 * 0.15 = 9.0 kWh
        assertThat(reserve).isEqualTo(9.0);
    }

    @Test
    @DisplayName("Current energy is capacity times state of charge")
    void shouldCalculateCurrentEnergy() {
        double energyAt50 = BatterySafetyCalculator.calculateCurrentEnergy(CAPACITY, 0.50);
        assertThat(energyAt50).isEqualTo(30.0);

        double energyAt100 = BatterySafetyCalculator.calculateCurrentEnergy(CAPACITY, 1.00);
        assertThat(energyAt100).isEqualTo(60.0);

        double energyAt0 = BatterySafetyCalculator.calculateCurrentEnergy(CAPACITY, 0.00);
        assertThat(energyAt0).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Available energy above reserve at 50% SoC")
    void shouldCalculateAvailableEnergyAboveReserve() {
        double available = BatterySafetyCalculator.calculateAvailableEnergyAboveReserve(CAPACITY, 0.50);
        // 30.0 - 9.0 = 21.0 kWh
        assertThat(available).isEqualTo(21.0);
    }

    @Test
    @DisplayName("Boundary: Exact 15% reserve yields exactly 0.0 usable energy above reserve")
    void exact15PercentReserveYieldsZeroAvailableEnergy() {
        double available = BatterySafetyCalculator.calculateAvailableEnergyAboveReserve(CAPACITY, 0.15);
        assertThat(available).isCloseTo(0.0, offset(1e-12));
        assertThat(BatterySafetyCalculator.isAboveOrAtReserve(0.15)).isTrue();
    }

    @Test
    @DisplayName("Boundary: Values just below 15% (e.g. 14.9999%) yield negative usable energy and violate reserve")
    void valuesJustBelow15PercentViolateReserve() {
        double socBelow = 0.1499999;
        double available = BatterySafetyCalculator.calculateAvailableEnergyAboveReserve(CAPACITY, socBelow);

        assertThat(available).isNegative();
        // Conservative check: 0.1499999 MUST NOT be rounded up or accepted as safe
        assertThat(BatterySafetyCalculator.isAboveOrAtReserve(socBelow)).isFalse();
    }

    @Test
    @DisplayName("canSustainTrip checks if available energy above reserve satisfies energy demand")
    void shouldEvaluateTripSustainability() {
        // At 50% SoC on 60 kWh battery: available energy is 21.0 kWh
        // A trip requiring 20.0 kWh is sustainable
        assertThat(BatterySafetyCalculator.canSustainTrip(CAPACITY, 0.50, 20.0)).isTrue();

        // A trip requiring exactly 21.0 kWh is sustainable (leaves battery at exact 15% floor)
        assertThat(BatterySafetyCalculator.canSustainTrip(CAPACITY, 0.50, 21.0)).isTrue();

        // A trip requiring 21.0001 kWh violates the 15% reserve floor
        assertThat(BatterySafetyCalculator.canSustainTrip(CAPACITY, 0.50, 21.0001)).isFalse();

        // A vehicle already at 14% SoC cannot sustain any positive trip
        assertThat(BatterySafetyCalculator.canSustainTrip(CAPACITY, 0.14, 0.1)).isFalse();
    }

    @Test
    @DisplayName("Max feasible distance calculation")
    void shouldCalculateMaxFeasibleDistance() {
        // 60 kWh @ 50% SoC -> 21 kWh available above reserve
        // At 0.15 kWh/km -> 21 / 0.15 = 140 km
        double maxDist = BatterySafetyCalculator.calculateMaxFeasibleDistanceKm(CAPACITY, 0.50, 0.15);
        assertThat(maxDist).isCloseTo(140.0, offset(1e-9));

        // When already below reserve, feasible distance is 0.0
        double maxDistBelow = BatterySafetyCalculator.calculateMaxFeasibleDistanceKm(CAPACITY, 0.14, 0.15);
        assertThat(maxDistBelow).isEqualTo(0.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, -1.0, 2.0, Double.NaN})
    @DisplayName("Reject invalid state of charge outside [0.0, 1.0]")
    void shouldRejectInvalidSoc(double invalidSoc) {
        assertThatThrownBy(() -> BatterySafetyCalculator.calculateCurrentEnergy(CAPACITY, invalidSoc))
                .isInstanceOf(InvalidEnergyCalculationException.class);

        assertThatThrownBy(() -> BatterySafetyCalculator.isAboveOrAtReserve(invalidSoc))
                .isInstanceOf(InvalidEnergyCalculationException.class);
    }

    @Test
    @DisplayName("Reject non-positive battery capacity")
    void shouldRejectNonPositiveCapacity() {
        assertThatThrownBy(() -> BatterySafetyCalculator.calculateMinimumReserveEnergy(0.0))
                .isInstanceOf(InvalidEnergyCalculationException.class);

        assertThatThrownBy(() -> BatterySafetyCalculator.calculateMinimumReserveEnergy(-50.0))
                .isInstanceOf(InvalidEnergyCalculationException.class);
    }
}
