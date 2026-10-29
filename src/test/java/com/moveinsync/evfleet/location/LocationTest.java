package com.moveinsync.evfleet.location;

import com.moveinsync.evfleet.exception.InvalidLocationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class LocationTest {

    @Test
    @DisplayName("Valid coordinates create Location record")
    void shouldCreateValidLocation() {
        Location location = new Location(12.9279, 77.6826);
        assertThat(location.latitude()).isEqualTo(12.9279);
        assertThat(location.longitude()).isEqualTo(77.6826);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.001, 90.001, 120.0, -100.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Reject invalid latitude values")
    void shouldRejectInvalidLatitude(double invalidLat) {
        assertThatThrownBy(() -> new Location(invalidLat, 77.0))
                .isInstanceOf(InvalidLocationException.class)
                .hasMessageContaining("Latitude");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.001, 180.001, 200.0, -190.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Reject invalid longitude values")
    void shouldRejectInvalidLongitude(double invalidLon) {
        assertThatThrownBy(() -> new Location(12.0, invalidLon))
                .isInstanceOf(InvalidLocationException.class)
                .hasMessageContaining("Longitude");
    }

    @Test
    @DisplayName("Identical points have zero Haversine distance")
    void shouldReturnZeroForIdenticalPoints() {
        Location bellandur = new Location(12.9279, 77.6826);
        assertThat(bellandur.distanceTo(bellandur)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Haversine distance is strictly symmetric: dist(A, B) == dist(B, A)")
    void shouldBeSymmetric() {
        Location london = new Location(51.5074, -0.1278);
        Location paris = new Location(48.8566, 2.3522);

        double dist1 = london.distanceTo(paris);
        double dist2 = paris.distanceTo(london);

        assertThat(dist1).isEqualTo(dist2);
    }

    @Test
    @DisplayName("Known geographic benchmark: London to Paris is ~343.5 km")
    void shouldCalculateAccurateDistanceLondonParis() {
        Location london = new Location(51.5074, -0.1278);
        Location paris = new Location(48.8566, 2.3522);

        double distance = london.distanceTo(paris);
        assertThat(distance).isCloseTo(343.5, offset(1.0));
    }

    @Test
    @DisplayName("Known local benchmark: Bangalore Kempegowda Airport to Bellandur is ~30.2 km")
    void shouldCalculateAccurateDistanceBangaloreAirportToBellandur() {
        Location airport = new Location(13.1986, 77.7066);
        Location bellandur = new Location(12.9279, 77.6826);

        double distance = airport.distanceTo(bellandur);
        assertThat(distance).isCloseTo(30.2, offset(0.5));
    }

    @Test
    @DisplayName("Null destination location throws InvalidLocationException")
    void shouldThrowWhenDestinationIsNull() {
        Location location = new Location(12.0, 77.0);
        assertThatThrownBy(() -> location.distanceTo(null))
                .isInstanceOf(InvalidLocationException.class)
                .hasMessageContaining("Destination location");
    }
}
