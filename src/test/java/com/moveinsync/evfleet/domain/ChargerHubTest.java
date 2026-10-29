package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidChargerHubException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargerHubTest {

    private final Location hubLocation = new Location(12.9279, 77.6826);
    private TariffSchedule tariffSchedule;

    @BeforeEach
    void setUp() {
        tariffSchedule = TariffSchedule.ofFlatRate(ZoneId.of("Asia/Kolkata"), TariffRate.of(7.50, "INR"));
    }

    @Test
    @DisplayName("Valid charger hub creation")
    void shouldCreateValidChargerHub() {
        ChargerHub hub = new ChargerHub(
                "HUB-BELLANDUR",
                "TENANT-BLR",
                hubLocation,
                10,
                60.0,
                tariffSchedule
        );

        assertThat(hub.getHubId()).isEqualTo("HUB-BELLANDUR");
        assertThat(hub.getTenantId()).isEqualTo("TENANT-BLR");
        assertThat(hub.getLocation()).isEqualTo(hubLocation);
        assertThat(hub.getChargerCount()).isEqualTo(10);
        assertThat(hub.getChargerPowerKw()).isEqualTo(60.0);
        assertThat(hub.getTariffSchedule()).isEqualTo(tariffSchedule);
    }

    @Test
    @DisplayName("Distinguish number of chargers from individual charger power rating")
    void shouldDistinguishChargerCountFromPowerRating() {
        // 10 chargers each rated at 60 kW
        ChargerHub hub = new ChargerHub("HUB-1", "TENANT-1", hubLocation, 10, 60.0, tariffSchedule);

        assertThat(hub.getChargerCount()).isEqualTo(10);
        assertThat(hub.getChargerPowerKw()).isEqualTo(60.0);
        // Total potential power across all chargers concurrently is 600 kW
        assertThat(hub.getTotalPotentialPowerKw()).isEqualTo(600.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    @DisplayName("Reject non-positive charger count")
    void shouldRejectNonPositiveChargerCount(int invalidCount) {
        assertThatThrownBy(() -> new ChargerHub("HUB-1", "TENANT-1", hubLocation, invalidCount, 60.0, tariffSchedule))
                .isInstanceOf(InvalidChargerHubException.class)
                .hasMessageContaining("Charger count");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -50.0, -0.01, Double.NaN})
    @DisplayName("Reject non-positive charger power")
    void shouldRejectNonPositiveChargerPower(double invalidPower) {
        assertThatThrownBy(() -> new ChargerHub("HUB-1", "TENANT-1", hubLocation, 10, invalidPower, tariffSchedule))
                .isInstanceOf(InvalidChargerHubException.class)
                .hasMessageContaining("Charger power");
    }

    @Test
    @DisplayName("Reject null location or null tariff schedule")
    void shouldRejectNullLocationOrSchedule() {
        assertThatThrownBy(() -> new ChargerHub("HUB-1", "TENANT-1", null, 10, 60.0, tariffSchedule))
                .isInstanceOf(InvalidChargerHubException.class);

        assertThatThrownBy(() -> new ChargerHub("HUB-1", "TENANT-1", hubLocation, 10, 60.0, null))
                .isInstanceOf(InvalidChargerHubException.class);
    }
}
