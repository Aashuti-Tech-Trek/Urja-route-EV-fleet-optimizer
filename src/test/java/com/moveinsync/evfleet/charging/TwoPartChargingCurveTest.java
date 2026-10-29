package com.moveinsync.evfleet.charging;

import com.moveinsync.evfleet.exception.InvalidChargingSessionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class TwoPartChargingCurveTest {

    private TwoPartChargingCurve curve;
    private static final double CAPACITY = 60.0;     // 60 kWh battery
    private static final double CHARGER_KW = 60.0;   // 60 kW DC Fast charger

    @BeforeEach
    void setUp() {
        // Fast multiplier = 1.0 (60 kW), Taper multiplier = 0.5 (30 kW) above 80%
        curve = new TwoPartChargingCurve();
    }

    @Test
    @DisplayName("Case 1: Charging entirely below 80% (0.20 to 0.70)")
    void shouldCalculateChargingEntirelyBelow80Percent() {
        double startSoc = 0.20;
        double targetSoc = 0.70;

        double energyDelivered = curve.calculateEnergyDeliveredKwh(CAPACITY, startSoc, targetSoc);
        double energyBelow = curve.calculateEnergyBelowThreshold(CAPACITY, startSoc, targetSoc);
        double energyAbove = curve.calculateEnergyAboveThreshold(CAPACITY, startSoc, targetSoc);
        double timeHours = curve.calculateChargingTimeHours(CAPACITY, startSoc, targetSoc, CHARGER_KW);

        // 60 kWh * (0.70 - 0.20) = 30.0 kWh
        assertThat(energyDelivered).isCloseTo(30.0, offset(1e-9));
        assertThat(energyBelow).isCloseTo(30.0, offset(1e-9));
        assertThat(energyAbove).isCloseTo(0.0, offset(1e-9));

        // At 60 kW: 30 / 60 = 0.5 hours (30 minutes)
        assertThat(timeHours).isCloseTo(0.5, offset(1e-9));
    }

    @Test
    @DisplayName("Case 2: Charging entirely above 80% (0.82 to 0.92)")
    void shouldCalculateChargingEntirelyAbove80Percent() {
        double startSoc = 0.82;
        double targetSoc = 0.92;

        double energyDelivered = curve.calculateEnergyDeliveredKwh(CAPACITY, startSoc, targetSoc);
        double energyBelow = curve.calculateEnergyBelowThreshold(CAPACITY, startSoc, targetSoc);
        double energyAbove = curve.calculateEnergyAboveThreshold(CAPACITY, startSoc, targetSoc);
        double timeHours = curve.calculateChargingTimeHours(CAPACITY, startSoc, targetSoc, CHARGER_KW);

        // 60 kWh * 0.10 = 6.0 kWh
        assertThat(energyDelivered).isCloseTo(6.0, offset(1e-9));
        assertThat(energyBelow).isCloseTo(0.0, offset(1e-9));
        assertThat(energyAbove).isCloseTo(6.0, offset(1e-9));

        // At 50% taper (30 kW): 6.0 / 30.0 = 0.2 hours (12 minutes)
        assertThat(timeHours).isCloseTo(0.2, offset(1e-9));
    }

    @Test
    @DisplayName("Case 3: Charging across 80% threshold (0.20 to 0.90)")
    void shouldCalculateChargingAcross80PercentThreshold() {
        double startSoc = 0.20;
        double targetSoc = 0.90;

        double energyDelivered = curve.calculateEnergyDeliveredKwh(CAPACITY, startSoc, targetSoc);
        double energyBelow = curve.calculateEnergyBelowThreshold(CAPACITY, startSoc, targetSoc);
        double energyAbove = curve.calculateEnergyAboveThreshold(CAPACITY, startSoc, targetSoc);
        double timeHours = curve.calculateChargingTimeHours(CAPACITY, startSoc, targetSoc, CHARGER_KW);

        // Below 80%: 60 * 0.60 = 36.0 kWh -> 36 / 60 = 0.6 hours
        // Above 80%: 60 * 0.10 = 6.0 kWh  -> 6 / 30 = 0.2 hours
        // Total = 42.0 kWh, Total time = 0.8 hours (48 minutes)
        assertThat(energyDelivered).isCloseTo(42.0, offset(1e-9));
        assertThat(energyBelow).isCloseTo(36.0, offset(1e-9));
        assertThat(energyAbove).isCloseTo(6.0, offset(1e-9));
        assertThat(timeHours).isCloseTo(0.8, offset(1e-9));

        Duration duration = curve.calculateChargingDuration(CAPACITY, startSoc, targetSoc, CHARGER_KW);
        assertThat(duration.toMinutes()).isEqualTo(48);
    }

    @Test
    @DisplayName("Case 4: Starting at exactly 80% (0.80 to 0.95)")
    void shouldCalculateChargingStartingAtExact80Percent() {
        double startSoc = 0.80;
        double targetSoc = 0.95;

        double energyBelow = curve.calculateEnergyBelowThreshold(CAPACITY, startSoc, targetSoc);
        double energyAbove = curve.calculateEnergyAboveThreshold(CAPACITY, startSoc, targetSoc);
        double timeHours = curve.calculateChargingTimeHours(CAPACITY, startSoc, targetSoc, CHARGER_KW);

        // 60 * 0.15 = 9.0 kWh all above 80%
        assertThat(energyBelow).isCloseTo(0.0, offset(1e-9));
        assertThat(energyAbove).isCloseTo(9.0, offset(1e-9));
        // At 30 kW: 9.0 / 30 = 0.3 hours (18 minutes)
        assertThat(timeHours).isCloseTo(0.3, offset(1e-9));
    }

    @Test
    @DisplayName("Case 5: Targeting exactly 80% (0.30 to 0.80)")
    void shouldCalculateChargingTargetingExact80Percent() {
        double startSoc = 0.30;
        double targetSoc = 0.80;

        double energyBelow = curve.calculateEnergyBelowThreshold(CAPACITY, startSoc, targetSoc);
        double energyAbove = curve.calculateEnergyAboveThreshold(CAPACITY, startSoc, targetSoc);
        double timeHours = curve.calculateChargingTimeHours(CAPACITY, startSoc, targetSoc, CHARGER_KW);

        // 60 * 0.50 = 30.0 kWh all below 80%
        assertThat(energyBelow).isCloseTo(30.0, offset(1e-9));
        assertThat(energyAbove).isCloseTo(0.0, offset(1e-9));
        // At 60 kW: 30 / 60 = 0.5 hours (30 minutes)
        assertThat(timeHours).isCloseTo(0.5, offset(1e-9));
    }

    @Test
    @DisplayName("Case 6: No charging required (startSoc == targetSoc)")
    void shouldHandleNoChargingRequired() {
        double timeHours = curve.calculateChargingTimeHours(CAPACITY, 0.65, 0.65, CHARGER_KW);
        double energy = curve.calculateEnergyDeliveredKwh(CAPACITY, 0.65, 0.65);

        assertThat(timeHours).isEqualTo(0.0);
        assertThat(energy).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Case 7: Resulting SoC inversion logic across threshold")
    void shouldInvertTimeIntoResultingSocAccurately() {
        // Charging for 0.8 hours from 0.20 SoC should yield 0.90 SoC
        double resultingSoc = curve.calculateResultingSoc(CAPACITY, 0.20, 0.80, CHARGER_KW);
        assertThat(resultingSoc).isCloseTo(0.90, offset(1e-9));

        // Overcharging is capped at 1.00 (100%)
        double overchargedSoc = curve.calculateResultingSoc(CAPACITY, 0.20, 10.0, CHARGER_KW);
        assertThat(overchargedSoc).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Reject invalid target SoC below starting SoC")
    void shouldRejectTargetBelowStart() {
        assertThatThrownBy(() -> curve.calculateChargingTimeHours(CAPACITY, 0.80, 0.70, CHARGER_KW))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("cannot be less than starting SoC");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.01, -0.01, 1.5, Double.NaN})
    @DisplayName("Reject target SoC or start SoC outside [0.0, 1.0]")
    void shouldRejectSocOutOfRange(double invalidSoc) {
        assertThatThrownBy(() -> curve.calculateChargingTimeHours(CAPACITY, 0.50, invalidSoc, CHARGER_KW))
                .isInstanceOf(InvalidChargingSessionException.class);

        assertThatThrownBy(() -> curve.calculateChargingTimeHours(CAPACITY, invalidSoc, 0.80, CHARGER_KW))
                .isInstanceOf(InvalidChargingSessionException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -10.0, -1.0, Double.NaN})
    @DisplayName("Reject zero or negative charger power")
    void shouldRejectInvalidChargerPower(double invalidPower) {
        assertThatThrownBy(() -> curve.calculateChargingTimeHours(CAPACITY, 0.20, 0.80, invalidPower))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("Charger power");
    }
}
