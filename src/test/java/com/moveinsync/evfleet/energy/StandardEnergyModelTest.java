package com.moveinsync.evfleet.energy;

import com.moveinsync.evfleet.exception.InvalidEnergyCalculationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class StandardEnergyModelTest {

    private StandardEnergyModel energyModel;
    private static final double BASE_CONSUMPTION = 0.15; // 0.15 kWh/km typical EV cab

    @BeforeEach
    void setUp() {
        energyModel = new StandardEnergyModel();
    }

    @Test
    @DisplayName("Baseline energy calculation with neutral traffic and AC")
    void shouldCalculateBaselineEnergy() {
        EnergyFactors factors = EnergyFactors.standard();
        double distanceKm = 50.0;

        double effectiveConsumption = energyModel.calculateEffectiveConsumption(BASE_CONSUMPTION, factors);
        double energyRequired = energyModel.calculateEnergyRequired(distanceKm, BASE_CONSUMPTION, factors);

        assertThat(effectiveConsumption).isEqualTo(0.15);
        assertThat(energyRequired).isCloseTo(7.5, offset(1e-9));
    }

    @Test
    @DisplayName("Energy calculation under heavy traffic multiplier (1.30x)")
    void shouldCalculateTrafficAdjustedEnergy() {
        EnergyFactors factors = EnergyFactors.of(1.30, 1.0);
        double distanceKm = 50.0;

        double effectiveConsumption = energyModel.calculateEffectiveConsumption(BASE_CONSUMPTION, factors);
        double energyRequired = energyModel.calculateEnergyRequired(distanceKm, BASE_CONSUMPTION, factors);

        // 0.15 * 1.30 = 0.195 kWh/km
        assertThat(effectiveConsumption).isCloseTo(0.195, offset(1e-9));
        // 50 * 0.195 = 9.75 kWh
        assertThat(energyRequired).isCloseTo(9.75, offset(1e-9));
    }

    @Test
    @DisplayName("Energy calculation under summer AC multiplier (1.20x)")
    void shouldCalculateAcAdjustedEnergy() {
        EnergyFactors factors = EnergyFactors.of(1.0, 1.20);
        double distanceKm = 50.0;

        double effectiveConsumption = energyModel.calculateEffectiveConsumption(BASE_CONSUMPTION, factors);
        double energyRequired = energyModel.calculateEnergyRequired(distanceKm, BASE_CONSUMPTION, factors);

        // 0.15 * 1.20 = 0.180 kWh/km
        assertThat(effectiveConsumption).isCloseTo(0.180, offset(1e-9));
        // 50 * 0.18 = 9.0 kWh
        assertThat(energyRequired).isCloseTo(9.0, offset(1e-9));
    }

    @Test
    @DisplayName("Combined traffic and AC multipliers without double-counting")
    void shouldCalculateCombinedAdjustedEnergy() {
        EnergyFactors factors = EnergyFactors.of(1.25, 1.20);
        double distanceKm = 50.0;

        double effectiveConsumption = energyModel.calculateEffectiveConsumption(BASE_CONSUMPTION, factors);
        double energyRequired = energyModel.calculateEnergyRequired(distanceKm, BASE_CONSUMPTION, factors);

        // 0.15 * 1.25 * 1.20 = 0.225 kWh/km
        assertThat(effectiveConsumption).isCloseTo(0.225, offset(1e-9));
        // 50 * 0.225 = 11.25 kWh
        assertThat(energyRequired).isCloseTo(11.25, offset(1e-9));
    }

    @Test
    @DisplayName("Zero distance requires zero energy")
    void zeroDistanceRequiresZeroEnergy() {
        double energy = energyModel.calculateEnergyRequired(0.0, BASE_CONSUMPTION, EnergyFactors.standard());
        assertThat(energy).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Reject negative distance and non-positive base consumption")
    void shouldRejectInvalidInputs() {
        assertThatThrownBy(() -> energyModel.calculateEnergyRequired(-10.0, BASE_CONSUMPTION, EnergyFactors.standard()))
                .isInstanceOf(InvalidEnergyCalculationException.class)
                .hasMessageContaining("Distance");

        assertThatThrownBy(() -> energyModel.calculateEnergyRequired(10.0, 0.0, EnergyFactors.standard()))
                .isInstanceOf(InvalidEnergyCalculationException.class)
                .hasMessageContaining("Base consumption");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.99, -1.0, 6.0, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("Reject invalid energy factor multipliers outside [1.0, 5.0]")
    void shouldRejectInvalidMultipliers(double invalidMultiplier) {
        assertThatThrownBy(() -> EnergyFactors.of(invalidMultiplier, 1.0))
                .isInstanceOf(InvalidEnergyCalculationException.class);

        assertThatThrownBy(() -> EnergyFactors.of(1.0, invalidMultiplier))
                .isInstanceOf(InvalidEnergyCalculationException.class);
    }
}
