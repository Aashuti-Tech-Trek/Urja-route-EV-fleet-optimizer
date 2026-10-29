package com.moveinsync.evfleet.time;

import com.moveinsync.evfleet.exception.InvalidTariffException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TariffScheduleTest {

    private ZoneId istZone;
    private TariffRate offPeakRate;
    private TariffRate standardRate;
    private TariffRate peakRate;
    private TariffSchedule schedule;

    @BeforeEach
    void setUp() {
        istZone = ZoneId.of("Asia/Kolkata");
        offPeakRate = TariffRate.of(5.00, "INR");
        standardRate = TariffRate.of(8.00, "INR");
        peakRate = TariffRate.of(12.00, "INR");

        // Schedule:
        // 22:00 -> 06:00 (spans midnight) = OFF_PEAK (5.00 INR/kWh)
        // 06:00 -> 18:00 = STANDARD (8.00 INR/kWh)
        // 18:00 -> 22:00 = PEAK (12.00 INR/kWh)
        TariffSlot nightSlot = new TariffSlot(LocalTime.of(22, 0), LocalTime.of(6, 0), offPeakRate, TariffType.OFF_PEAK);
        TariffSlot daySlot = new TariffSlot(LocalTime.of(6, 0), LocalTime.of(18, 0), standardRate, TariffType.STANDARD);
        TariffSlot peakSlot = new TariffSlot(LocalTime.of(18, 0), LocalTime.of(22, 0), peakRate, TariffType.PEAK);

        schedule = new TariffSchedule(istZone, "INR", List.of(nightSlot, daySlot, peakSlot), standardRate);
    }

    @Test
    @DisplayName("Reject negative tariff rates and invalid currency codes")
    void shouldRejectInvalidTariffRateAndCurrency() {
        assertThatThrownBy(() -> TariffRate.of(-1.0, "INR"))
                .isInstanceOf(InvalidTariffException.class)
                .hasMessageContaining("non-negative");

        assertThatThrownBy(() -> TariffRate.of(5.0, "INVALID_CURRENCY"))
                .isInstanceOf(InvalidTariffException.class)
                .hasMessageContaining("Invalid ISO-4217");
    }

    @Test
    @DisplayName("Reject currency mismatch between schedule and slot rates")
    void shouldRejectCurrencyMismatch() {
        TariffRate usdRate = TariffRate.of(0.12, "USD");
        TariffSlot usdSlot = new TariffSlot(LocalTime.of(0, 0), LocalTime.of(12, 0), usdRate, TariffType.STANDARD);

        assertThatThrownBy(() -> new TariffSchedule(istZone, "INR", List.of(usdSlot), standardRate))
                .isInstanceOf(InvalidTariffException.class)
                .hasMessageContaining("does not match schedule currency");
    }

    @Test
    @DisplayName("Midnight-crossing slot correctly matches local night times")
    void shouldMatchMidnightCrossingSlot() {
        // 23:30 IST is 18:00 UTC
        Instant nightInstant1 = Instant.parse("2026-10-29T18:00:00Z"); // 23:30 IST
        // 03:00 IST is 21:30 UTC previous day
        Instant nightInstant2 = Instant.parse("2026-10-29T21:30:00Z"); // 03:00 IST next morning

        assertThat(schedule.getRateAt(nightInstant1)).isEqualTo(offPeakRate);
        assertThat(schedule.getRateAt(nightInstant2)).isEqualTo(offPeakRate);

        // 12:00 IST (06:30 UTC) is standard
        Instant dayInstant = Instant.parse("2026-10-29T06:30:00Z");
        assertThat(schedule.getRateAt(dayInstant)).isEqualTo(standardRate);

        // 20:00 IST (14:30 UTC) is peak
        Instant peakInstant = Instant.parse("2026-10-29T14:30:00Z");
        assertThat(schedule.getRateAt(peakInstant)).isEqualTo(peakRate);
    }

    @Test
    @DisplayName("Calculate charging cost entirely within a single tariff slot")
    void shouldCalculateCostWithinSingleSlot() {
        // Charging 20 kWh from 07:00 IST to 09:00 IST (all in standard rate: 8.00 INR/kWh)
        // 07:00 IST = 01:30 UTC, 09:00 IST = 03:30 UTC
        Instant start = Instant.parse("2026-10-29T01:30:00Z");
        Instant end = Instant.parse("2026-10-29T03:30:00Z");

        BigDecimal cost = schedule.calculateCost(start, end, 20.0);
        // Expected: 20 * 8.00 = 160.00 INR
        assertThat(cost).isEqualByComparingTo(new BigDecimal("160.0000"));
    }

    @Test
    @DisplayName("Calculate charging cost across tariff boundary (Standard to Peak)")
    void shouldCalculateCostAcrossTariffBoundary() {
        // Charging 20 kWh from 17:00 IST to 19:00 IST (2 hours total)
        // 17:00 to 18:00 IST is STANDARD (8.00 INR/kWh) -> 10 kWh * 8.00 = 80.00 INR
        // 18:00 to 19:00 IST is PEAK (12.00 INR/kWh) -> 10 kWh * 12.00 = 120.00 INR
        // Total expected = 200.00 INR
        // 17:00 IST = 11:30 UTC, 19:00 IST = 13:30 UTC
        Instant start = Instant.parse("2026-10-29T11:30:00Z");
        Instant end = Instant.parse("2026-10-29T13:30:00Z");

        BigDecimal cost = schedule.calculateCost(start, end, 20.0);
        assertThat(cost).isEqualByComparingTo(new BigDecimal("200.0000"));
    }

    @Test
    @DisplayName("Calculate charging cost across midnight inside off-peak slot")
    void shouldCalculateCostAcrossMidnight() {
        // Charging 30 kWh from 23:00 IST to 02:00 IST (3 hours)
        // Both hours fall in OFF_PEAK (5.00 INR/kWh)
        // 23:00 IST = 17:30 UTC, 02:00 IST = 20:30 UTC
        Instant start = Instant.parse("2026-10-29T17:30:00Z");
        Instant end = Instant.parse("2026-10-29T20:30:00Z");

        BigDecimal cost = schedule.calculateCost(start, end, 30.0);
        // Expected: 30 * 5.00 = 150.00 INR
        assertThat(cost).isEqualByComparingTo(new BigDecimal("150.0000"));
    }

    @Test
    @DisplayName("Zero energy or zero duration results in zero cost")
    void zeroEnergyOrDurationResultsInZeroCost() {
        Instant t = Instant.parse("2026-10-29T10:00:00Z");
        assertThat(schedule.calculateCost(t, t, 10.0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(schedule.calculateCost(t, t.plusSeconds(3600), 0.0)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Daylight Saving Time transition handled seamlessly in London timezone")
    void shouldHandleDaylightSavingTransition() {
        // Europe/London DST transition: 2026-10-25 clocks go back 1 hour from BST (UTC+1) to GMT (UTC+0) at 02:00
        ZoneId londonZone = ZoneId.of("Europe/London");
        TariffRate gbpRate = TariffRate.of(0.25, "GBP");
        TariffSchedule londonSchedule = TariffSchedule.ofFlatRate(londonZone, gbpRate);

        Instant start = Instant.parse("2026-10-25T00:00:00Z");
        Instant end = Instant.parse("2026-10-25T04:00:00Z");

        BigDecimal cost = londonSchedule.calculateCost(start, end, 40.0);
        // 40 kWh * 0.25 GBP = 10.00 GBP
        assertThat(cost).isEqualByComparingTo(new BigDecimal("10.0000"));
    }
}
