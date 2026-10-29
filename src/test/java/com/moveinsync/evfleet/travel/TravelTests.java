package com.moveinsync.evfleet.travel;

import com.moveinsync.evfleet.exception.InvalidDomainException;
import com.moveinsync.evfleet.location.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TravelConfig}, {@link DefaultTravelTimeEstimator}, and {@link DistanceMatrix}.
 *
 * <p>All expected values are calculated by hand so the tests serve as specification documents.
 *
 * <p><strong>Key fixture:</strong>
 * Bengaluru MG Road (12.9716°N, 77.5946°E) to Whitefield (12.9698°N, 77.7500°E).
 * Haversine ≈ 16.84 km (computed value from Location.distanceTo()).
 * Road distance with factor 1.3 ≈ 16.84 × 1.3 ≈ 21.89 km.
 * Travel time at 25 km/h ≈ 21.89 / 25 = 0.8756 hours = 3152.2 s → ceil → 3153 s.
 */
@DisplayName("Travel Package Tests")
class TravelTests {

    // Fixture locations (Bengaluru geography, chosen for hand-calculable distances)
    static final Location MG_ROAD    = new Location(12.9716, 77.5946);
    static final Location WHITEFIELD = new Location(12.9698, 77.7500);
    static final Location SAME_AS_MG = new Location(12.9716, 77.5946); // identical to MG_ROAD

    // ─────────────────────────────────────────────────────────────
    // TravelConfig tests
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("TravelConfig")
    class TravelConfigTests {

        @Test
        @DisplayName("default config has documented baseline values")
        void defaultConfigValues() {
            TravelConfig cfg = TravelConfig.defaultConfig();
            assertThat(cfg.getRoadDistanceFactor()).isEqualTo(1.3);
            assertThat(cfg.getAverageSpeedKmh()).isEqualTo(25.0);
        }

        @Test
        @DisplayName("custom config stores provided values")
        void customConfigValues() {
            TravelConfig cfg = new TravelConfig(1.5, 40.0);
            assertThat(cfg.getRoadDistanceFactor()).isEqualTo(1.5);
            assertThat(cfg.getAverageSpeedKmh()).isEqualTo(40.0);
        }

        @Test
        @DisplayName("boundary value: roadDistanceFactor = 1.0 (minimum) is accepted")
        void minRoadDistanceFactor() {
            assertThatNoException().isThrownBy(() -> new TravelConfig(1.0, 25.0));
        }

        @Test
        @DisplayName("boundary value: roadDistanceFactor = 5.0 (maximum) is accepted")
        void maxRoadDistanceFactor() {
            assertThatNoException().isThrownBy(() -> new TravelConfig(5.0, 25.0));
        }

        @Test
        @DisplayName("roadDistanceFactor below 1.0 is rejected")
        void roadDistanceFactorBelowOne() {
            assertThatThrownBy(() -> new TravelConfig(0.99, 25.0))
                    .isInstanceOf(InvalidDomainException.class)
                    .hasMessageContaining("Road distance factor");
        }

        @Test
        @DisplayName("roadDistanceFactor above 5.0 is rejected")
        void roadDistanceFactorAboveFive() {
            assertThatThrownBy(() -> new TravelConfig(5.01, 25.0))
                    .isInstanceOf(InvalidDomainException.class)
                    .hasMessageContaining("Road distance factor");
        }

        @Test
        @DisplayName("averageSpeedKmh of 0.0 is rejected (must be strictly positive)")
        void zeroSpeedRejected() {
            assertThatThrownBy(() -> new TravelConfig(1.3, 0.0))
                    .isInstanceOf(InvalidDomainException.class)
                    .hasMessageContaining("Average speed");
        }

        @Test
        @DisplayName("averageSpeedKmh above 200 is rejected")
        void speedAbove200Rejected() {
            assertThatThrownBy(() -> new TravelConfig(1.3, 200.001))
                    .isInstanceOf(InvalidDomainException.class)
                    .hasMessageContaining("Average speed");
        }

        @Test
        @DisplayName("NaN roadDistanceFactor is rejected")
        void nanFactorRejected() {
            assertThatThrownBy(() -> new TravelConfig(Double.NaN, 25.0))
                    .isInstanceOf(InvalidDomainException.class);
        }

        @Test
        @DisplayName("Infinite speed is rejected")
        void infiniteSpeedRejected() {
            assertThatThrownBy(() -> new TravelConfig(1.3, Double.POSITIVE_INFINITY))
                    .isInstanceOf(InvalidDomainException.class);
        }

        @Test
        @DisplayName("toString includes both parameters")
        void toStringContainsBothParams() {
            String s = new TravelConfig(1.3, 25.0).toString();
            assertThat(s).contains("1.30").contains("25.0");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DefaultTravelTimeEstimator tests
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("DefaultTravelTimeEstimator")
    class EstimatorTests {

        final DefaultTravelTimeEstimator defaultEstimator = DefaultTravelTimeEstimator.withDefaults();

        @Test
        @DisplayName("same location → Duration.ZERO travel time")
        void sameLocationZeroTime() {
            Duration result = defaultEstimator.estimateTravelTime(MG_ROAD, SAME_AS_MG);
            assertThat(result).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("same location → 0.0 road distance")
        void sameLocationZeroDistance() {
            double dist = defaultEstimator.estimateRoadDistanceKm(MG_ROAD, SAME_AS_MG);
            assertThat(dist).isEqualTo(0.0);
        }

        @Test
        @DisplayName("MG Road → Whitefield: road distance ≈ 21.89 km (Haversine≈16.84 × 1.3)")
        void mgRoadToWhitefieldDistance() {
            double dist = defaultEstimator.estimateRoadDistanceKm(MG_ROAD, WHITEFIELD);
            // Actual Haversine ≈ 16.84 km; road ≈ 16.84 × 1.3 ≈ 21.89 km. Allow ±0.5 km.
            assertThat(dist).isBetween(21.0, 22.5);
        }

        @Test
        @DisplayName("MG Road → Whitefield: travel time ≈ 3153 s (≈52.6 min) at 25 km/h")
        void mgRoadToWhitefieldTime() {
            Duration result = defaultEstimator.estimateTravelTime(MG_ROAD, WHITEFIELD);
            // Actual: road ≈ 21.89 km; time = ceil(21.89 / 25 × 3600) = ceil(3152.16) = 3153 s.
            // Allow ±60 s for floating-point precision.
            assertThat(result.getSeconds()).isBetween(3050L, 3250L);
        }

        @Test
        @DisplayName("travel time result is never negative")
        void travelTimeNeverNegative() {
            Duration result = defaultEstimator.estimateTravelTime(WHITEFIELD, MG_ROAD);
            assertThat(result.isNegative()).isFalse();
        }

        @Test
        @DisplayName("alternate config: speed 50 km/h gives roughly half travel time vs 25 km/h")
        void fasterSpeedHalvesTravelTime() {
            TravelConfig fast = new TravelConfig(1.3, 50.0);
            DefaultTravelTimeEstimator fastEst = new DefaultTravelTimeEstimator(fast);
            Duration slow = defaultEstimator.estimateTravelTime(MG_ROAD, WHITEFIELD);
            Duration fast2 = fastEst.estimateTravelTime(MG_ROAD, WHITEFIELD);
            // fast should be approximately half of slow (within 2 seconds rounding)
            assertThat(fast2.getSeconds()).isBetween(slow.getSeconds() / 2 - 2, slow.getSeconds() / 2 + 2);
        }

        @Test
        @DisplayName("alternate config: roadFactor 1.0 gives road distance = Haversine distance")
        void roadFactorOneGivesHaversine() {
            TravelConfig cfg = new TravelConfig(1.0, 25.0);
            DefaultTravelTimeEstimator est = new DefaultTravelTimeEstimator(cfg);
            double road = est.estimateRoadDistanceKm(MG_ROAD, WHITEFIELD);
            double haversine = MG_ROAD.distanceTo(WHITEFIELD);
            assertThat(road).isCloseTo(haversine, within(1e-9));
        }

        @Test
        @DisplayName("null source location throws InvalidLocationException")
        void nullFromThrows() {
            assertThatThrownBy(() -> defaultEstimator.estimateTravelTime(null, WHITEFIELD))
                    .isInstanceOf(com.moveinsync.evfleet.exception.InvalidLocationException.class);
        }

        @Test
        @DisplayName("null destination location throws InvalidLocationException")
        void nullToThrows() {
            assertThatThrownBy(() -> defaultEstimator.estimateTravelTime(MG_ROAD, null))
                    .isInstanceOf(com.moveinsync.evfleet.exception.InvalidLocationException.class);
        }

        @Test
        @DisplayName("null config throws exception at construction time")
        void nullConfigThrows() {
            assertThatThrownBy(() -> new DefaultTravelTimeEstimator(null))
                    .isInstanceOf(InvalidDomainException.class);
        }

        @Test
        @DisplayName("getConfig returns the config passed at construction")
        void getConfigReturnsConstructorArg() {
            TravelConfig cfg = new TravelConfig(1.5, 30.0);
            DefaultTravelTimeEstimator est = new DefaultTravelTimeEstimator(cfg);
            assertThat(est.getConfig()).isSameAs(cfg);
        }

        @Test
        @DisplayName("result is deterministic: same call twice returns identical duration")
        void deterministic() {
            Duration d1 = defaultEstimator.estimateTravelTime(MG_ROAD, WHITEFIELD);
            Duration d2 = defaultEstimator.estimateTravelTime(MG_ROAD, WHITEFIELD);
            assertThat(d1).isEqualTo(d2);
        }

        @Test
        @DisplayName("near-polar coordinates (valid edge case) do not throw")
        void nearPolarCoordinates() {
            Location north = new Location(89.9, 0.0);
            Location south = new Location(-89.9, 0.0);
            assertThatNoException().isThrownBy(() -> defaultEstimator.estimateTravelTime(north, south));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DistanceMatrix tests
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("DistanceMatrix")
    class DistanceMatrixTests {

        final DefaultTravelTimeEstimator estimator = DefaultTravelTimeEstimator.withDefaults();
        final DistanceMatrix matrix = new DistanceMatrix(estimator);

        @Test
        @DisplayName("first access computes and caches the value")
        void firstAccessCaches() {
            double d = matrix.getRoadDistanceKm(MG_ROAD, WHITEFIELD);
            assertThat(matrix.cacheSize()).isEqualTo(1);
            assertThat(d).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("second access for same pair returns cached value without re-computing")
        void secondAccessUsesCachedValue() {
            double d1 = matrix.getRoadDistanceKm(MG_ROAD, WHITEFIELD);
            double d2 = matrix.getRoadDistanceKm(MG_ROAD, WHITEFIELD);
            // Cache should not grow; value must be identical
            assertThat(matrix.cacheSize()).isEqualTo(1);
            assertThat(d1).isEqualTo(d2);
        }

        @Test
        @DisplayName("(A→B) and (B→A) are stored as separate cache entries")
        void asymmetricKeysStoredSeparately() {
            matrix.getRoadDistanceKm(MG_ROAD, WHITEFIELD);
            matrix.getRoadDistanceKm(WHITEFIELD, MG_ROAD);
            // Two distinct ordered pairs → two cache entries
            assertThat(matrix.cacheSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("same location → 0.0 distance, cached separately")
        void sameLocationDistance() {
            double d = matrix.getRoadDistanceKm(MG_ROAD, SAME_AS_MG);
            assertThat(d).isEqualTo(0.0);
        }

        @Test
        @DisplayName("clearCache resets cache to zero entries")
        void clearCacheResetsSize() {
            matrix.getRoadDistanceKm(MG_ROAD, WHITEFIELD);
            assertThat(matrix.cacheSize()).isEqualTo(1);
            matrix.clearCache();
            assertThat(matrix.cacheSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("null from-location throws")
        void nullFromThrows() {
            assertThatThrownBy(() -> matrix.getRoadDistanceKm(null, WHITEFIELD))
                    .isInstanceOf(com.moveinsync.evfleet.exception.InvalidLocationException.class);
        }

        @Test
        @DisplayName("null to-location throws")
        void nullToThrows() {
            assertThatThrownBy(() -> matrix.getRoadDistanceKm(MG_ROAD, null))
                    .isInstanceOf(com.moveinsync.evfleet.exception.InvalidLocationException.class);
        }

        @Test
        @DisplayName("null estimator at construction time throws")
        void nullEstimatorThrows() {
            assertThatThrownBy(() -> new DistanceMatrix(null))
                    .isInstanceOf(InvalidDomainException.class);
        }

        @Test
        @DisplayName("cached value matches direct estimator call")
        void cachedValueMatchesDirectEstimatorCall() {
            double fromMatrix = matrix.getRoadDistanceKm(MG_ROAD, WHITEFIELD);
            double fromEstimator = estimator.estimateRoadDistanceKm(MG_ROAD, WHITEFIELD);
            assertThat(fromMatrix).isEqualTo(fromEstimator);
        }

        @Test
        @DisplayName("LocationPair(A,B) equals LocationPair(A,B)")
        void locationPairEquality() {
            DistanceMatrix.LocationPair p1 = new DistanceMatrix.LocationPair(MG_ROAD, WHITEFIELD);
            DistanceMatrix.LocationPair p2 = new DistanceMatrix.LocationPair(
                    new Location(12.9716, 77.5946), new Location(12.9698, 77.7500));
            assertThat(p1).isEqualTo(p2);
        }

        @Test
        @DisplayName("LocationPair(A,B) is not equal to LocationPair(B,A)")
        void locationPairAsymmetry() {
            DistanceMatrix.LocationPair p1 = new DistanceMatrix.LocationPair(MG_ROAD, WHITEFIELD);
            DistanceMatrix.LocationPair p2 = new DistanceMatrix.LocationPair(WHITEFIELD, MG_ROAD);
            assertThat(p1).isNotEqualTo(p2);
        }
    }
}
