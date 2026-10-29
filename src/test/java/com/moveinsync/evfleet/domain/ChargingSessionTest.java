package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidChargingSessionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargingSessionTest {

    private final Instant start = Instant.parse("2026-10-29T14:00:00Z");
    private final Instant end = Instant.parse("2026-10-29T14:48:00Z"); // 48 minutes

    @Test
    @DisplayName("Valid charging session creation and metrics")
    void shouldCreateValidChargingSession() {
        ChargingSession session = new ChargingSession(
                "SESS-001",
                "VEH-001",
                "HUB-001",
                start,
                end,
                0.20,
                0.90,
                42.0,
                new BigDecimal("336.00"),
                "INR"
        );

        assertThat(session.getSessionId()).isEqualTo("SESS-001");
        assertThat(session.getVehicleId()).isEqualTo("VEH-001");
        assertThat(session.getHubId()).isEqualTo("HUB-001");
        assertThat(session.getStartTime()).isEqualTo(start);
        assertThat(session.getEndTime()).isEqualTo(end);
        assertThat(session.getDuration()).isEqualTo(Duration.ofMinutes(48));
        assertThat(session.getStartSoc()).isEqualTo(0.20);
        assertThat(session.getEndSoc()).isEqualTo(0.90);
        assertThat(session.getDeltaSoc()).isEqualTo(0.70);
        assertThat(session.getEnergyDeliveredKwh()).isEqualTo(42.0);
        assertThat(session.getEstimatedCost()).isEqualByComparingTo(new BigDecimal("336.00"));
        assertThat(session.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("Reject session end time before or equal to start time")
    void shouldRejectInvalidSessionInterval() {
        assertThatThrownBy(() -> new ChargingSession("S-1", "V-1", "H-1", end, start, 0.20, 0.80, 36.0, BigDecimal.TEN, "INR"))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("strictly after start time");

        assertThatThrownBy(() -> new ChargingSession("S-1", "V-1", "H-1", start, start, 0.20, 0.80, 36.0, BigDecimal.TEN, "INR"))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("strictly after start time");
    }

    @Test
    @DisplayName("Reject ending SoC strictly lower than starting SoC")
    void shouldRejectEndingSocLowerThanStart() {
        assertThatThrownBy(() -> new ChargingSession("S-1", "V-1", "H-1", start, end, 0.80, 0.70, 6.0, BigDecimal.TEN, "INR"))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("cannot be lower than starting SoC");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, -10.0, Double.NaN})
    @DisplayName("Reject negative energy delivered")
    void shouldRejectNegativeEnergy(double invalidEnergy) {
        assertThatThrownBy(() -> new ChargingSession("S-1", "V-1", "H-1", start, end, 0.20, 0.80, invalidEnergy, BigDecimal.TEN, "INR"))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("Energy delivered");
    }

    @Test
    @DisplayName("Reject negative estimated cost")
    void shouldRejectNegativeCost() {
        assertThatThrownBy(() -> new ChargingSession("S-1", "V-1", "H-1", start, end, 0.20, 0.80, 30.0, new BigDecimal("-5.00"), "INR"))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("Estimated cost cannot be negative");
    }

    @Test
    @DisplayName("Session respects vehicle battery capacity bounds")
    void shouldRespectVehicleBatteryCapacity() {
        // Vehicle battery is 60 kWh. Delta SoC is 0.70 (0.20 to 0.90) -> max physical energy is 42.0 kWh
        ChargingSession validSession = new ChargingSession("S-1", "V-1", "H-1", start, end, 0.20, 0.90, 42.0, BigDecimal.TEN, "INR");
        validSession.validateAgainstBatteryCapacity(60.0); // should succeed without error

        // If recorded energy is 55.0 kWh for a 0.70 SoC delta on a 60 kWh battery (which physically can only absorb 42 kWh)
        ChargingSession excessiveSession = new ChargingSession("S-2", "V-1", "H-1", start, end, 0.20, 0.90, 55.0, BigDecimal.TEN, "INR");
        assertThatThrownBy(() -> excessiveSession.validateAgainstBatteryCapacity(60.0))
                .isInstanceOf(InvalidChargingSessionException.class)
                .hasMessageContaining("exceeds physical battery capacity");
    }
}
