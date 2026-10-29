package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidTripException;
import com.moveinsync.evfleet.location.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTest {

    private final Location pickup = new Location(12.9279, 77.6826);
    private final Location dropoff = new Location(13.1986, 77.7066);
    private final Instant startTime = Instant.parse("2026-10-29T18:00:00Z");
    private final Instant finishTime = Instant.parse("2026-10-29T19:30:00Z");

    @Test
    @DisplayName("Valid trip creation")
    void shouldCreateValidTrip() {
        Trip trip = new Trip(
                "TRIP-101",
                "TENANT-BLR",
                pickup,
                dropoff,
                30.5,
                startTime,
                finishTime
        );

        assertThat(trip.getTripId()).isEqualTo("TRIP-101");
        assertThat(trip.getTenantId()).isEqualTo("TENANT-BLR");
        assertThat(trip.getStartLocation()).isEqualTo(pickup);
        assertThat(trip.getDestination()).isEqualTo(dropoff);
        assertThat(trip.getDistanceKm()).isEqualTo(30.5);
        assertThat(trip.getScheduledStartTime()).isEqualTo(startTime);
        assertThat(trip.getLatestAllowedFinishTime()).isEqualTo(finishTime);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Reject blank trip ID or tenant ID")
    void shouldRejectBlankIdentifiers(String blankId) {
        assertThatThrownBy(() -> new Trip(blankId, "TENANT-1", pickup, dropoff, 30.0, startTime, finishTime))
                .isInstanceOf(InvalidTripException.class)
                .hasMessageContaining("Trip ID");

        assertThatThrownBy(() -> new Trip("TRIP-1", blankId, pickup, dropoff, 30.0, startTime, finishTime))
                .isInstanceOf(InvalidTripException.class)
                .hasMessageContaining("Tenant ID");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -5.0, -0.01, Double.NaN})
    @DisplayName("Reject non-positive trip distance")
    void shouldRejectNonPositiveDistance(double invalidDist) {
        assertThatThrownBy(() -> new Trip("TRIP-1", "TENANT-1", pickup, dropoff, invalidDist, startTime, finishTime))
                .isInstanceOf(InvalidTripException.class)
                .hasMessageContaining("Distance");
    }

    @Test
    @DisplayName("Reject finish time before or equal to scheduled start time")
    void shouldRejectInvalidTimeSequence() {
        // Finish time before start time
        assertThatThrownBy(() -> new Trip("TRIP-1", "TENANT-1", pickup, dropoff, 30.0, finishTime, startTime))
                .isInstanceOf(InvalidTripException.class)
                .hasMessageContaining("strictly after scheduled start time");

        // Finish time equal to start time
        assertThatThrownBy(() -> new Trip("TRIP-1", "TENANT-1", pickup, dropoff, 30.0, startTime, startTime))
                .isInstanceOf(InvalidTripException.class)
                .hasMessageContaining("strictly after scheduled start time");
    }

    @Test
    @DisplayName("Reject null locations")
    void shouldRejectNullLocations() {
        assertThatThrownBy(() -> new Trip("TRIP-1", "TENANT-1", null, dropoff, 30.0, startTime, finishTime))
                .isInstanceOf(InvalidTripException.class)
                .hasMessageContaining("Start location");

        assertThatThrownBy(() -> new Trip("TRIP-1", "TENANT-1", pickup, null, 30.0, startTime, finishTime))
                .isInstanceOf(InvalidTripException.class)
                .hasMessageContaining("Destination");
    }
}
