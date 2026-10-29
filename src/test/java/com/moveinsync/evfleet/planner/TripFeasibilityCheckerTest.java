package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.domain.VehicleAvailability;
import com.moveinsync.evfleet.domain.VehicleSpecification;
import com.moveinsync.evfleet.domain.VehicleState;
import com.moveinsync.evfleet.domain.VehicleStatus;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.StandardEnergyModel;
import com.moveinsync.evfleet.exception.InvalidDomainException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.travel.DefaultTravelTimeEstimator;
import com.moveinsync.evfleet.travel.DistanceMatrix;
import com.moveinsync.evfleet.travel.TravelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TripFeasibilityChecker}.
 *
 * <p><strong>Fixture convention:</strong>
 * All fixtures use small, hand-calculable numbers so expected values can be independently verified.
 *
 * <p><strong>Energy model baseline:</strong>
 * StandardEnergyModel with EnergyFactors(trafficMultiplier=1.0, acMultiplier=1.0).
 * Effective consumption = base × 1.0 × 1.0 = base.
 *
 * <p><strong>Travel config:</strong>
 * roadDistanceFactor=1.0 (road distance = Haversine) to make energy calculations deterministic
 * and directly tied to geographic distance without a scaling multiplier.
 * averageSpeedKmh=60 km/h so travel time is distance / 60 h = distance × 60 s/km.
 *
 * <p><strong>Vehicle fixture:</strong>
 * batteryCapacity=60 kWh, base consumption=0.2 kWh/km, SoC varies per test.
 * 15% reserve = 60 × 0.15 = 9 kWh. Available above reserve at 100% SoC = 51 kWh.
 */
@DisplayName("TripFeasibilityChecker Tests")
class TripFeasibilityCheckerTest {

    // Locations — chosen so Haversine distance is approximately known
    // DEPOT and PICKUP are ~10 km apart (approximate; exact values from distanceTo)
    static final Location DEPOT  = new Location(12.9716, 77.5946); // MG Road, Bengaluru
    static final Location PICKUP = new Location(12.9716, 77.6842); // ~10 km east, same latitude
    static final Location DEST   = new Location(12.9716, 77.7738); // ~10 km further east

    // roadFactor=1.0 → road distance = Haversine distance directly.
    // speed=36 km/h (valid range) → each km takes exactly 100 seconds (36 km/h = 0.01 km/s).
    // This makes timing arithmetic simple and keeps tests within TravelConfig validity constraints.
    static final TravelConfig UNIT_TIME_CONFIG = new TravelConfig(1.0, 36.0);

    static final EnergyFactors STANDARD_FACTORS = EnergyFactors.standard();

    TripFeasibilityChecker checker;
    DistanceMatrix distanceMatrix;
    DefaultTravelTimeEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new DefaultTravelTimeEstimator(UNIT_TIME_CONFIG);
        distanceMatrix = new DistanceMatrix(estimator);
        checker = new TripFeasibilityChecker(
                new StandardEnergyModel(), STANDARD_FACTORS, estimator, distanceMatrix);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper factories
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a vehicle at DEPOT with given SoC and availability.
     * batteryCapacity=60 kWh, baseConsumption=0.2 kWh/km.
     */
    Vehicle vehicleAt(Location location, double soc, Instant availableFrom) {
        return vehicleAt(location, soc, availableFrom, VehicleStatus.AVAILABLE);
    }

    Vehicle vehicleAt(Location location, double soc, Instant availableFrom, VehicleStatus status) {
        VehicleSpecification spec = new VehicleSpecification(60.0, 0.2);
        VehicleAvailability avail = new VehicleAvailability(availableFrom, location, status);
        VehicleState state = new VehicleState(location, soc, avail);
        return new Vehicle("V1", "tenant1", spec, state);
    }

    /**
     * Creates a trip from PICKUP to DEST.
     * distanceKm is explicitly set so energy math is not tied to Haversine.
     */
    Trip tripFrom(Location start, Location dest, double distanceKm,
                  Instant scheduledStart, Instant latestFinish) {
        return new Trip("T1", "tenant1", start, dest, distanceKm, scheduledStart, latestFinish);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Feasible baseline
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("FEASIBLE cases")
    class FeasibleCases {

        @Test
        @DisplayName("1. One vehicle, one feasible trip — all constraints satisfied")
        void oneFeasibleTrip() {
            // Vehicle at DEPOT, SoC=0.90 (54 kWh), available now.
            // speed=36 km/h. Haversine(DEPOT, PICKUP) ≈ 8.78 km → travel ≈ 879 s.
            // Trip starts in 1800 s (30 min) → vehicle arrives at ~879 s → arrives with 921 s to spare.
            // Trip distance = 5 km → trip energy = 0.2 × 5 = 1.0 kWh
            // Empty drive energy = 0.2 × ~8.78 ≈ 1.756 kWh
            // Total energy ≈ 2.756 kWh << available (54 - 9) = 45 kWh
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = now.plusSeconds(1800);   // 30 min from now - plenty for empty drive
            Instant finish = Instant.parse("2026-01-01T19:00:00Z");  // ample deadline

            Vehicle v = vehicleAt(DEPOT, 0.90, now);
            Trip t = tripFrom(PICKUP, DEST, 5.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isTrue();
            assertThat(result.getStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.NONE);
            // Computed values must be present
            assertThat(result.getEmptyDriveEnergyKwh()).isPresent();
            assertThat(result.getTripEnergyKwh()).isPresent().hasValueSatisfying(e -> assertThat(e).isCloseTo(1.0, within(0.01)));
            assertThat(result.getSocAfterTrip()).isPresent().hasValueSatisfying(s -> assertThat(s).isGreaterThan(0.15));
            assertThat(result.getDeadlineSlack()).isPresent().hasValueSatisfying(slack -> assertThat(slack.isNegative()).isFalse());
        }

        @Test
        @DisplayName("Vehicle already at pickup — zero empty drive")
        void vehicleAlreadyAtPickup() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = Instant.parse("2026-01-01T18:00:00Z");
            Instant finish = Instant.parse("2026-01-01T19:00:00Z");

            Vehicle v = vehicleAt(PICKUP, 0.80, now);
            Trip t = tripFrom(PICKUP, DEST, 5.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isTrue();
            // Zero empty drive → empty drive energy = 0
            assertThat(result.getEmptyDriveEnergyKwh()).isPresent().hasValue(0.0);
            assertThat(result.getEmptyDriveDistanceKm()).isPresent().hasValue(0.0);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VEHICLE_UNAVAILABLE
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("VEHICLE_UNAVAILABLE")
    class VehicleUnavailable {

        @Test
        @DisplayName("Vehicle in CHARGING status → VEHICLE_UNAVAILABLE")
        void vehicleCharging() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = Instant.parse("2026-01-01T19:00:00Z");
            Instant finish = Instant.parse("2026-01-01T20:00:00Z");

            Vehicle v = vehicleAt(DEPOT, 0.50, now, VehicleStatus.CHARGING);
            Trip t = tripFrom(PICKUP, DEST, 5.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.VEHICLE_UNAVAILABLE);
            assertThat(result.getExplanation()).containsIgnoringCase("CHARGING");
        }

        @Test
        @DisplayName("Vehicle in ON_TRIP status → VEHICLE_UNAVAILABLE")
        void vehicleOnTrip() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = Instant.parse("2026-01-01T19:00:00Z");
            Instant finish = Instant.parse("2026-01-01T20:00:00Z");

            Vehicle v = vehicleAt(DEPOT, 0.70, now, VehicleStatus.ON_TRIP);
            Trip t = tripFrom(PICKUP, DEST, 5.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.VEHICLE_UNAVAILABLE);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PICKUP_UNREACHABLE
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("PICKUP_UNREACHABLE")
    class PickupUnreachable {

        @Test
        @DisplayName("2. Empty travel time causes late arrival at pickup")
        void emptyDriveTooLong() {
            // Vehicle available NOW, but trip starts in 3 seconds.
            // speed=36 km/h. Haversine(DEPOT, PICKUP) ≈ 8.78 km → travel ≈ 879 s.
            // Vehicle will arrive at pickup in ~879 s, but trip starts in 3 s → late.
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = now.plusSeconds(3);  // trip starts in 3 s — not enough travel time
            Instant finish = now.plusSeconds(7200);

            Vehicle v = vehicleAt(DEPOT, 0.90, now);
            Trip t = tripFrom(PICKUP, DEST, 5.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.PICKUP_UNREACHABLE);
            assertThat(result.getExplanation()).containsIgnoringCase("late");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DEADLINE_INFEASIBLE
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("DEADLINE_INFEASIBLE")
    class DeadlineInfeasible {

        @Test
        @DisplayName("4. Trip deadline is impossible given trip distance")
        void deadlineTooTight() {
            // Trip distance = 50 km; at speed=36 km/h → travel time = 50/36 × 3600 = 5000 s.
            // But latestAllowedFinishTime = scheduledStart + 10 s → impossible.
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = now.plusSeconds(3600);   // vehicle already at pickup; plenty of time
            Instant finish = start.plusSeconds(10);  // deadline is only 10 s after start

            Vehicle v = vehicleAt(PICKUP, 0.90, now); // vehicle already at pickup → no empty drive
            Trip t = tripFrom(PICKUP, DEST, 50.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.DEADLINE_INFEASIBLE);
            assertThat(result.getExplanation()).containsIgnoringCase("latestAllowedFinishTime");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE")
    class InsufficientEnergyEmptyDrive {

        @Test
        @DisplayName("2b. Empty drive energy exceeds available energy above reserve")
        void emptyDriveDepletesReserve() {
            // Vehicle SoC = 0.16 → current energy = 60 × 0.16 = 9.6 kWh
            // Reserve = 9 kWh → available above reserve = 0.6 kWh
            // Empty drive distance ≈ 8.78 km → energy = 0.2 × 8.78 ≈ 1.756 kWh
            // 1.756 kWh > 0.6 kWh available → INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            // DEPOT to PICKUP travel time ≈ 879 s at 36 km/h, so schedule start comfortably after
            Instant start = now.plusSeconds(1800);
            Instant finish = start.plusSeconds(7200);

            Vehicle v = vehicleAt(DEPOT, 0.16, now);
            Trip t = tripFrom(PICKUP, DEST, 5.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE);
            assertThat(result.getExplanation()).containsIgnoringCase("reserve");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // INSUFFICIENT_ENERGY_FOR_TRIP
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("INSUFFICIENT_ENERGY_FOR_TRIP")
    class InsufficientEnergyTrip {

        @Test
        @DisplayName("3. Trip energy violates reserve after empty drive")
        void tripEnergyViolatesReserve() {
            // Vehicle already at PICKUP (zero empty drive).
            // SoC = 0.20 → current energy = 12 kWh; reserve = 9 kWh; available = 3 kWh.
            // Trip distance = 20 km → energy = 0.2 × 20 = 4.0 kWh > 3 kWh available.
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = now;
            Instant finish = now.plusSeconds(7200);

            Vehicle v = vehicleAt(PICKUP, 0.20, now);
            Trip t = tripFrom(PICKUP, DEST, 20.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP);
            assertThat(result.getExplanation())
                    .containsIgnoringCase("reserve")
                    .contains("T1");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Null / invalid inputs
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Invalid input handling")
    class InvalidInputs {

        @Test
        @DisplayName("15a. Null vehicle → INVALID_INPUT")
        void nullVehicle() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Trip t = tripFrom(PICKUP, DEST, 5.0, now, now.plusSeconds(3600));
            FeasibilityResult result = checker.check(null, t);
            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.INVALID_INPUT);
        }

        @Test
        @DisplayName("15b. Null trip → INVALID_INPUT")
        void nullTrip() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = vehicleAt(DEPOT, 0.80, now);
            FeasibilityResult result = checker.check(v, null);
            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.INVALID_INPUT);
        }

        @Test
        @DisplayName("Null energy model at construction throws")
        void nullEnergyModelThrows() {
            assertThatThrownBy(() -> new TripFeasibilityChecker(null, STANDARD_FACTORS, estimator, distanceMatrix))
                    .isInstanceOf(InvalidDomainException.class);
        }

        @Test
        @DisplayName("Null energy factors at construction throws")
        void nullEnergyFactorsThrows() {
            assertThatThrownBy(() -> new TripFeasibilityChecker(new StandardEnergyModel(), null, estimator, distanceMatrix))
                    .isInstanceOf(InvalidDomainException.class);
        }

        @Test
        @DisplayName("Null travel estimator at construction throws")
        void nullEstimatorThrows() {
            assertThatThrownBy(() -> new TripFeasibilityChecker(new StandardEnergyModel(), STANDARD_FACTORS, null, distanceMatrix))
                    .isInstanceOf(InvalidDomainException.class);
        }

        @Test
        @DisplayName("Null distance matrix at construction throws")
        void nullDistanceMatrixThrows() {
            assertThatThrownBy(() -> new TripFeasibilityChecker(new StandardEnergyModel(), STANDARD_FACTORS, estimator, null))
                    .isInstanceOf(InvalidDomainException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SoC and energy boundary conditions
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("SoC boundary conditions")
    class SocBoundaries {

        @Test
        @DisplayName("Vehicle at exactly 15% SoC with no trip energy needed → VEHICLE_AVAILABLE but no trip energy")
        void exactlyAtReserveWithZeroTrip() {
            // SoC = 0.15 → exactly at reserve → available above reserve = 0.
            // Vehicle at PICKUP (zero empty drive), trip distance = 0 would violate Trip invariant.
            // Use tiny 0.0001 km trip with 0.2 kWh/km → energy ≈ 0.00002 kWh << 0 available.
            // Actually Trip.distanceKm must be strictly positive. Use 0.001 km.
            // Energy = 0.2 × 0.001 = 0.0002 kWh > 0 available → INFEASIBLE.
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = now;
            Instant finish = now.plusSeconds(7200);

            Vehicle v = vehicleAt(PICKUP, 0.15, now);
            Trip t = tripFrom(PICKUP, DEST, 0.001, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP);
        }

        @Test
        @DisplayName("Vehicle with high SoC can sustain both empty drive and long trip")
        void highSocSustainsBoth() {
            // SoC = 1.0 → 60 kWh; reserve = 9 kWh; available = 51 kWh.
            // Empty drive ≈ 8.78 km → energy ≈ 1.756 kWh.
            // Trip = 100 km → energy = 20 kWh. Total ≈ 21.756 kWh << 51 kWh available.
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            // Empty drive ≈ 879 s at 36 km/h; 100 km trip takes 10000 s at 36 km/h
            Instant start = now.plusSeconds(1000);
            Instant finish = start.plusSeconds(12000);

            Vehicle v = vehicleAt(DEPOT, 1.0, now);
            Trip t = tripFrom(PICKUP, DEST, 100.0, start, finish);

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isTrue();
            assertThat(result.getSocAfterTrip()).isPresent()
                    .hasValueSatisfying(s -> assertThat(s).isGreaterThanOrEqualTo(0.15));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Determinism
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("11. Same input → identical result on two consecutive calls")
        void sameInputSameResult() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant start = now.plusSeconds(1000);
            Instant finish = start.plusSeconds(7200);

            Vehicle v = vehicleAt(DEPOT, 0.90, now);
            Trip t = tripFrom(PICKUP, DEST, 10.0, start, finish);

            FeasibilityResult r1 = checker.check(v, t);
            FeasibilityResult r2 = checker.check(v, t);

            assertThat(r1.isFeasible()).isEqualTo(r2.isFeasible());
            assertThat(r1.getReason()).isEqualTo(r2.getReason());
            assertThat(r1.getEmptyDriveEnergyKwh()).isEqualTo(r2.getEmptyDriveEnergyKwh());
            assertThat(r1.getSocAfterTrip()).isEqualTo(r2.getSocAfterTrip());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Result content assertions
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Result structure")
    class ResultStructure {

        @Test
        @DisplayName("Infeasible result has no planning values (all Optional.empty)")
        void infeasibleResultHasNoValues() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = vehicleAt(PICKUP, 0.15, now);
            Trip t = tripFrom(PICKUP, DEST, 50.0, now, now.plusSeconds(7200));

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getEmptyDriveDistanceKm()).isEmpty();
            assertThat(result.getEmptyDriveEnergyKwh()).isEmpty();
            assertThat(result.getTripEnergyKwh()).isEmpty();
            assertThat(result.getSocAfterEmptyDrive()).isEmpty();
            assertThat(result.getSocAfterTrip()).isEmpty();
        }

        @Test
        @DisplayName("Feasible result always has all planning values present")
        void feasibleResultHasAllValues() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = vehicleAt(PICKUP, 0.90, now);
            Trip t = tripFrom(PICKUP, DEST, 5.0, now, now.plusSeconds(7200));

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isTrue();
            assertThat(result.getEmptyDriveDistanceKm()).isPresent();
            assertThat(result.getEmptyDriveEnergyKwh()).isPresent();
            assertThat(result.getTripEnergyKwh()).isPresent();
            assertThat(result.getSocAfterEmptyDrive()).isPresent();
            assertThat(result.getSocAfterTrip()).isPresent();
            assertThat(result.getEstimatedEmptyDriveTime()).isPresent();
            assertThat(result.getDeadlineSlack()).isPresent();
        }

        @Test
        @DisplayName("SoC after trip is within [0.15, 1.0] for a feasible result")
        void socAfterTripInValidRange() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = vehicleAt(PICKUP, 0.90, now);
            Trip t = tripFrom(PICKUP, DEST, 5.0, now, now.plusSeconds(7200));

            FeasibilityResult result = checker.check(v, t);

            assertThat(result.isFeasible()).isTrue();
            double socAfterTrip = result.getSocAfterTrip().orElseThrow();
            assertThat(socAfterTrip).isBetween(0.15, 1.0);
        }
    }
}
