package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.charging.TwoPartChargingCurve;
import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.energy.BatterySafetyCalculator;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.StandardEnergyModel;
import com.moveinsync.evfleet.exception.InvalidDomainException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.travel.DefaultTravelTimeEstimator;
import com.moveinsync.evfleet.travel.DistanceMatrix;
import com.moveinsync.evfleet.travel.TravelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Independent Quality Gate and Deep Audit Test Suite for Phase 2.
 *
 * <p>Audits every functional area:
 * <ul>
 *   <li>Section 2: Energy &amp; Battery Safety Calculations</li>
 *   <li>Section 3: Travel-Time &amp; Deadline Feasibility</li>
 *   <li>Section 4: Vehicle Assignment &amp; Lexicographic Objective</li>
 *   <li>Section 5: Charging Scheduling &amp; Hub Concurrency</li>
 *   <li>Section 6: Timeline Invariants &amp; Result Consistency</li>
 *   <li>Section 7: Local Search &amp; Determinism</li>
 *   <li>Section 8: DistanceMatrix &amp; Memory Modeling</li>
 *   <li>Section 9: Adversarial Inputs &amp; Fault Handling</li>
 *   <li>Section 10: Scalability Benchmark (up to target scale)</li>
 * </ul>
 */
@DisplayName("Phase 2 Quality Gate & Independent Audit")
class Phase2QualityGateAuditTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final TravelConfig TRAVEL_CONFIG = new TravelConfig(1.0, 36.0); // 36 km/h = 100 s/km
    private static final Location LOC_A = new Location(12.9716, 77.5946); // Bangalore MG Road
    private static final Location LOC_B = new Location(12.9352, 77.6245); // Koramangala
    private static final Location LOC_C = new Location(12.9279, 77.6271); // HSR Layout
    private static final Location LOC_D = new Location(12.9900, 77.6500); // Indiranagar

    private StandardEnergyModel energyModel;
    private EnergyFactors energyFactors;
    private DefaultTravelTimeEstimator estimator;
    private DistanceMatrix distanceMatrix;
    private TripFeasibilityChecker feasibilityChecker;
    private ChargingScheduler chargingScheduler;
    private GreedyFleetPlanner planner;

    @BeforeEach
    void setUp() {
        energyModel = new StandardEnergyModel();
        energyFactors = EnergyFactors.standard();
        estimator = new DefaultTravelTimeEstimator(TRAVEL_CONFIG);
        distanceMatrix = new DistanceMatrix(estimator);
        feasibilityChecker = new TripFeasibilityChecker(energyModel, energyFactors, estimator, distanceMatrix);
        chargingScheduler = new ChargingScheduler(energyModel, energyFactors, new TwoPartChargingCurve(), estimator, distanceMatrix);
        planner = new GreedyFleetPlanner(feasibilityChecker, chargingScheduler, distanceMatrix, estimator, energyModel, energyFactors, LocalSearchConfig.of(20, 1000));
    }

    private Vehicle createVehicle(String id, Location loc, double soc, Instant availableFrom) {
        return Vehicle.create(id, "TENANT-1", 60.0, 0.20, soc, loc, availableFrom);
    }

    private Trip createTrip(String id, Location from, Location to, double distKm, Instant start, Instant finish) {
        return new Trip(id, "TENANT-1", from, to, distKm, start, finish);
    }

    private ChargerHub createHub(String id, Location loc, int chargers, double powerKw, TariffSchedule schedule) {
        return new ChargerHub(id, "TENANT-1", loc, chargers, powerKw, schedule);
    }

    private TariffSchedule createFlatTariff(BigDecimal rate) {
        return TariffSchedule.ofFlatRate(UTC, new TariffRate(rate, "INR"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section 2: Energy & Battery Calculations Audit
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("2. Energy and Battery Calculations Audit")
    class EnergyAndBatteryAudit {

        @Test
        @DisplayName("Empty travel energy is included before passenger trip and reduces available SoC")
        void emptyTravelDeductedBeforeTrip() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = createVehicle("EV-1", LOC_A, 0.50, now); // 30 kWh
            Trip t = createTrip("T1", LOC_B, LOC_C, 10.0, now.plusSeconds(3600), now.plusSeconds(7200));

            FeasibilityResult result = feasibilityChecker.check(v, t);
            assertThat(result.isFeasible()).isTrue();

            double emptyDist = result.getEmptyDriveDistanceKm().orElseThrow();
            double emptyEnergy = result.getEmptyDriveEnergyKwh().orElseThrow();
            double tripEnergy = result.getTripEnergyKwh().orElseThrow();
            double socAfterEmpty = result.getSocAfterEmptyDrive().orElseThrow();
            double socAfterTrip = result.getSocAfterTrip().orElseThrow();

            // Hand calculation:
            // Empty distance > 0 (MG Road to Koramangala is ~5.35 km)
            assertThat(emptyDist).isGreaterThan(0.0);
            assertThat(emptyEnergy).isCloseTo(emptyDist * 0.20, within(1e-4));
            assertThat(tripEnergy).isCloseTo(10.0 * 0.20, within(1e-4)); // 2.0 kWh

            // Hand calculation SoC delta:
            assertThat(socAfterEmpty).isCloseTo(0.50 - (emptyEnergy / 60.0), within(1e-4));
            assertThat(socAfterTrip).isCloseTo(socAfterEmpty - (tripEnergy / 60.0), within(1e-4));
        }

        @Test
        @DisplayName("Traffic and AC multipliers scale consumption consistently")
        void trafficAndAcMultipliersScaleEnergy() {
            EnergyFactors heavyFactors = EnergyFactors.of(1.30, 1.20); // 1.30 * 1.20 = 1.56 multiplier
            StandardEnergyModel model = new StandardEnergyModel();

            double effectiveConsumption = model.calculateEffectiveConsumption(0.20, heavyFactors);
            assertThat(effectiveConsumption).isCloseTo(0.20 * 1.56, within(1e-4)); // 0.312 kWh/km

            double energy = model.calculateEnergyRequired(10.0, 0.20, heavyFactors);
            assertThat(energy).isCloseTo(3.12, within(1e-4));
        }

        @Test
        @DisplayName("Vehicle starting at exactly 15% cannot perform positive-energy trip without charging")
        void vehicleAtReserveCannotTravel() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = createVehicle("EV-1", LOC_B, 0.15, now); // exactly at 15% reserve
            Trip t = createTrip("T1", LOC_B, LOC_C, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));

            FeasibilityResult result = feasibilityChecker.check(v, t);
            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP);
        }

        @Test
        @DisplayName("Vehicle with enough energy for trip alone but empty drive breaches reserve is rejected")
        void emptyDriveBreachesReserveRejected() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            // Vehicle at LOC_A, SoC = 0.16 -> 9.6 kWh total, available above reserve = 0.6 kWh.
            // Empty drive LOC_A -> LOC_B (~5.35 km) requires ~1.07 kWh > 0.6 kWh.
            // Even though trip is only 1.0 km (0.2 kWh), empty drive alone violates reserve.
            Vehicle v = createVehicle("EV-1", LOC_A, 0.16, now);
            Trip t = createTrip("T1", LOC_B, LOC_C, 1.0, now.plusSeconds(1800), now.plusSeconds(3600));

            FeasibilityResult result = feasibilityChecker.check(v, t);
            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE);
        }

        @Test
        @DisplayName("Battery energy never becomes negative under any scenario")
        void batteryEnergyNeverNegative() {
            assertThat(BatterySafetyCalculator.calculateCurrentEnergy(60.0, 0.0)).isZero();
            assertThat(BatterySafetyCalculator.isAboveOrAtReserve(0.0)).isFalse();
            assertThat(BatterySafetyCalculator.isAboveOrAtReserve(0.149999)).isFalse();
            assertThat(BatterySafetyCalculator.isAboveOrAtReserve(0.150000)).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section 3: Travel-Time & Deadline Feasibility Audit
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("3. Travel-Time and Deadline Feasibility Audit")
    class TravelTimeAndDeadlineAudit {

        @Test
        @DisplayName("Same location travel has zero distance and zero duration")
        void sameLocationTravel() {
            double dist = distanceMatrix.getRoadDistanceKm(LOC_A, LOC_A);
            Duration duration = estimator.estimateTravelTime(LOC_A, LOC_A);

            assertThat(dist).isZero();
            assertThat(duration).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("Vehicle arriving exactly at scheduled start is feasible (boundary check)")
        void arrivalExactlyAtScheduledStart() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Duration travelTime = estimator.estimateTravelTime(LOC_A, LOC_B);
            Instant exactStart = now.plus(travelTime); // vehicle arrives exactly at start

            Vehicle v = createVehicle("EV-1", LOC_A, 0.90, now);
            Trip t = createTrip("T1", LOC_B, LOC_C, 10.0, exactStart, exactStart.plusSeconds(3600));

            FeasibilityResult result = feasibilityChecker.check(v, t);
            assertThat(result.isFeasible()).isTrue();
        }

        @Test
        @DisplayName("Vehicle arriving 1 second late for pickup is rejected with PICKUP_UNREACHABLE")
        void arrivalOneSecondLateRejected() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Duration travelTime = estimator.estimateTravelTime(LOC_A, LOC_B);
            Instant oneSecondTooEarly = now.plus(travelTime).minusSeconds(1);

            Vehicle v = createVehicle("EV-1", LOC_A, 0.90, now);
            Trip t = createTrip("T1", LOC_B, LOC_C, 10.0, oneSecondTooEarly, oneSecondTooEarly.plusSeconds(3600));

            FeasibilityResult result = feasibilityChecker.check(v, t);
            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.PICKUP_UNREACHABLE);
        }

        @Test
        @DisplayName("Trip finishing exactly at latest allowed finish time is feasible")
        void tripFinishingExactlyAtDeadline() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Duration tripDuration = estimator.estimateTravelTime(LOC_B, LOC_C);
            Instant finish = now.plus(tripDuration);

            Vehicle v = createVehicle("EV-1", LOC_B, 0.90, now);
            Trip t = createTrip("T1", LOC_B, LOC_C, 10.0, now, finish);

            FeasibilityResult result = feasibilityChecker.check(v, t);
            assertThat(result.isFeasible()).isTrue();
            assertThat(result.getDeadlineSlack()).isPresent().hasValue(Duration.ZERO);
        }

        @Test
        @DisplayName("Trip finishing after latest allowed finish time is rejected with DEADLINE_INFEASIBLE")
        void tripFinishingAfterDeadlineRejected() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Duration tripDuration = estimator.estimateTravelTime(LOC_B, LOC_C);
            Instant tightDeadline = now.plus(tripDuration).minusSeconds(5);

            Vehicle v = createVehicle("EV-1", LOC_B, 0.90, now);
            Trip t = createTrip("T1", LOC_B, LOC_C, 10.0, now, tightDeadline);

            FeasibilityResult result = feasibilityChecker.check(v, t);
            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getReason()).isEqualTo(FeasibilityReason.DEADLINE_INFEASIBLE);
        }

        @Test
        @DisplayName("Invalid coordinates, speed, and road factor fail immediately")
        void invalidTravelParametersRejected() {
            assertThatThrownBy(() -> new TravelConfig(0.9, 25.0))
                    .isInstanceOf(InvalidDomainException.class);
            assertThatThrownBy(() -> new TravelConfig(1.3, 0.0))
                    .isInstanceOf(InvalidDomainException.class);
            assertThatThrownBy(() -> new TravelConfig(1.3, 201.0))
                    .isInstanceOf(InvalidDomainException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section 4: Vehicle Assignment & Objective Ordering Audit
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("4. Vehicle Assignment and Objective Ordering Audit")
    class VehicleAssignmentAudit {

        @Test
        @DisplayName("Multiple vehicles competing for the same trip: lowest deadhead candidate chosen deterministically")
        void multipleVehiclesCompetingForTrip() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            // EV-1 is at LOC_A (further from LOC_C)
            // EV-2 is at LOC_B (closer to LOC_C)
            Vehicle v1 = createVehicle("EV-1", LOC_A, 0.90, now);
            Vehicle v2 = createVehicle("EV-2", LOC_B, 0.90, now);
            Trip t = createTrip("T1", LOC_C, LOC_D, 10.0, now.plusSeconds(3600), now.plusSeconds(7200));

            PlanningInput input = new PlanningInput("COMPETE-RUN", List.of(v1, v2), List.of(t));
            FleetPlanResult result = planner.plan(input);

            assertThat(result.getEvAssignments()).hasSize(1);
            // EV-2 is closer to LOC_C than EV-1 -> EV-2 must be chosen
            assertThat(result.getEvAssignments().get(0).getAssignedVehicle().getVehicleId()).isEqualTo("EV-2");
        }

        @Test
        @DisplayName("Equal deadhead candidates tie-break deterministically by vehicle ID")
        void equalCandidatesTieBreakByVehicleId() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            // EV-1 and EV-2 both at identical location LOC_A
            Vehicle v1 = createVehicle("EV-1", LOC_A, 0.90, now);
            Vehicle v2 = createVehicle("EV-2", LOC_A, 0.90, now);
            Trip t = createTrip("T1", LOC_A, LOC_B, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));

            PlanningInput input = new PlanningInput("TIE-RUN", List.of(v2, v1), List.of(t)); // passed in reverse
            FleetPlanResult result = planner.plan(input);

            // Deterministic tie-break picks EV-1 (alphabetical order)
            assertThat(result.getEvAssignments().get(0).getAssignedVehicle().getVehicleId()).isEqualTo("EV-1");
        }

        @Test
        @DisplayName("Local search improves an initial plan where greedy leaves a trip unassigned")
        void localSearchImprovesInitialPlan() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Instant t1Start = now.plusSeconds(1800);
            Instant t2Start = now.plusSeconds(2400);

            // EV-1 can serve T1 or T2
            // EV-2 can ONLY serve T1 (due to location/deadline), cannot reach T2 in time
            // If greedy assigns T1 to EV-1, then T2 has no vehicle -> 1 backup cab.
            // With local search priority perturbation, T2 gets assigned to EV-1, and T1 gets assigned to EV-2 -> 0 backup cabs!
            Vehicle v1 = createVehicle("EV-1", LOC_A, 0.90, now);
            Vehicle v2 = createVehicle("EV-2", LOC_C, 0.90, now);

            Trip t1 = createTrip("T1", LOC_C, LOC_B, 5.0, t1Start, t1Start.plusSeconds(1800));
            Trip t2 = createTrip("T2", LOC_A, LOC_D, 5.0, t2Start, t2Start.plusSeconds(1800));

            PlanningInput input = new PlanningInput("LS-IMPROVE-RUN", List.of(v1, v2), List.of(t1, t2));
            FleetPlanResult result = planner.plan(input);

            assertThat(result.getStatus()).isEqualTo(FleetPlanResult.PlanStatus.SUCCESS);
            assertThat(result.getTotalBackupTripCount()).isZero();
            assertThat(result.getEvAssignments()).hasSize(2);
            assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
        }

        @Test
        @DisplayName("Empty vehicle input supported gracefully: all trips assigned to backup")
        void emptyVehiclesAssignsAllToBackup() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Trip t = createTrip("T1", LOC_A, LOC_B, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));

            PlanningInput input = new PlanningInput("EMPTY-VEHICLES", List.of(), List.of(t));
            FleetPlanResult result = planner.plan(input);

            assertThat(result.getTotalBackupTripCount()).isEqualTo(1);
            assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
        }

        @Test
        @DisplayName("Empty trip input supported gracefully: zero assignments and SUCCESS status")
        void emptyTripsHandledGracefully() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = createVehicle("EV-1", LOC_A, 0.90, now);

            PlanningInput input = new PlanningInput("EMPTY-TRIPS", List.of(v), List.of());
            FleetPlanResult result = planner.plan(input);

            assertThat(result.getStatus()).isEqualTo(FleetPlanResult.PlanStatus.SUCCESS);
            assertThat(result.getTripAssignments()).isEmpty();
            assertThat(result.getTotalBackupTripCount()).isZero();
            assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section 5: Charging Scheduling & Charger Capacity Audit
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("5. Charging Scheduling and Charger Capacity Audit")
    class ChargingAndCapacityAudit {

        @Test
        @DisplayName("Adjacent charging sessions [18:00, 19:00) and [19:00, 20:00) do not conflict")
        void adjacentSessionsDoNotConflict() {
            ChargerOccupancyTracker tracker = new ChargerOccupancyTracker();
            Instant t1 = Instant.parse("2026-01-01T18:00:00Z");
            Instant t2 = Instant.parse("2026-01-01T19:00:00Z");
            Instant t3 = Instant.parse("2026-01-01T20:00:00Z");

            ChargingSession s1 = new ChargingSession("CS-1", "EV-1", "HUB-1", t1, t2, 0.20, 0.80, 36.0, BigDecimal.TEN, "INR");
            tracker.recordSession(s1);

            // Adjacent session starting exactly when s1 ends: [19:00, 20:00)
            boolean canSchedule = tracker.hasCapacity("HUB-1", t2, t3, 1);
            assertThat(canSchedule).isTrue();
        }

        @Test
        @DisplayName("Concurrent sessions at 1-charger hub are strictly prevented")
        void concurrencyCapEnforced() {
            ChargerOccupancyTracker tracker = new ChargerOccupancyTracker();
            Instant t1 = Instant.parse("2026-01-01T18:00:00Z");
            Instant t2 = Instant.parse("2026-01-01T19:00:00Z");

            ChargingSession s1 = new ChargingSession("CS-1", "EV-1", "HUB-1", t1, t2, 0.20, 0.80, 36.0, BigDecimal.TEN, "INR");
            tracker.recordSession(s1);

            // Overlapping session [18:30, 19:30) at hub with 1 charger
            Instant overlapStart = Instant.parse("2026-01-01T18:30:00Z");
            Instant overlapEnd = Instant.parse("2026-01-01T19:30:00Z");
            boolean canSchedule = tracker.hasCapacity("HUB-1", overlapStart, overlapEnd, 1);
            assertThat(canSchedule).isFalse();

            // At hub with 2 chargers, it is allowed
            boolean canSchedule2 = tracker.hasCapacity("HUB-1", overlapStart, overlapEnd, 2);
            assertThat(canSchedule2).isTrue();
        }

        @Test
        @DisplayName("Overlapping sessions at DIFFERENT hubs do not interfere with each other")
        void differentHubsDoNotInterfere() {
            ChargerOccupancyTracker tracker = new ChargerOccupancyTracker();
            Instant t1 = Instant.parse("2026-01-01T18:00:00Z");
            Instant t2 = Instant.parse("2026-01-01T19:00:00Z");

            ChargingSession s1 = new ChargingSession("CS-1", "EV-1", "HUB-1", t1, t2, 0.20, 0.80, 36.0, BigDecimal.TEN, "INR");
            tracker.recordSession(s1);

            // Same time at HUB-2
            boolean canScheduleAtHub2 = tracker.hasCapacity("HUB-2", t1, t2, 1);
            assertThat(canScheduleAtHub2).isTrue();
        }

        @Test
        @DisplayName("Charging entirely below 80% SoC uses pure fast rate")
        void chargingEntirelyBelow80() {
            TwoPartChargingCurve curve = new TwoPartChargingCurve();
            // 60 kWh battery, 50 kW charger, fast rate = 1.0 (50 kW)
            // 0.20 to 0.70 SoC: delta = 0.50 -> 30 kWh / 50 kW = 0.60 hours
            double time = curve.calculateChargingTimeHours(60.0, 0.20, 0.70, 50.0);
            assertThat(time).isCloseTo(0.60, within(1e-4));
        }

        @Test
        @DisplayName("Charging entirely above 80% SoC uses pure taper rate")
        void chargingEntirelyAbove80() {
            TwoPartChargingCurve curve = new TwoPartChargingCurve();
            // 60 kWh battery, 50 kW charger, taper rate = 0.50 (25 kW)
            // 0.80 to 0.95 SoC: delta = 0.15 -> 9 kWh / 25 kW = 0.36 hours
            double time = curve.calculateChargingTimeHours(60.0, 0.80, 0.95, 50.0);
            assertThat(time).isCloseTo(0.36, within(1e-4));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section 6 & 7: Timeline Invariants, Determinism & Local Search Audit
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("6 & 7. Timeline Invariants and Determinism Audit")
    class TimelineAndDeterminismAudit {

        @Test
        @DisplayName("Every input trip appears exactly once as either EV or backup assignment")
        void tripUniquenessAndCompleteness() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = createVehicle("EV-1", LOC_A, 0.90, now);
            Trip t1 = createTrip("T1", LOC_A, LOC_B, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));
            Trip t2 = createTrip("T2", LOC_C, LOC_D, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));

            PlanningInput input = new PlanningInput("RUN-COMPLETE", List.of(v), List.of(t1, t2));
            FleetPlanResult result = planner.plan(input);

            assertThat(result.getTripAssignments()).hasSize(2);
            long evCount = result.getTripAssignments().stream().filter(a -> a.isEvAssignment()).count();
            long backupCount = result.getTripAssignments().stream().filter(a -> a.isBackupAssignment()).count();

            assertThat(evCount + backupCount).isEqualTo(2);
            assertThat(result.getQualityReport().getTripsRequested()).isEqualTo(2);
            assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
        }

        @Test
        @DisplayName("Vehicle events are strictly chronological and maintain continuous location and SoC")
        void vehicleTimelineContinuityReconciled() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = createVehicle("EV-1", LOC_A, 0.90, now);
            Trip t1 = createTrip("T1", LOC_B, LOC_C, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));
            Trip t2 = createTrip("T2", LOC_C, LOC_D, 10.0, now.plusSeconds(5400), now.plusSeconds(7200));

            PlanningInput input = new PlanningInput("RUN-CONT", List.of(v), List.of(t1, t2));
            FleetPlanResult result = planner.plan(input);

            VehicleTimeline timeline = result.getVehicleTimelines().get("EV-1");
            List<TimelineEvent> events = timeline.getEvents();

            Instant lastTime = timeline.getInitialAvailableTime();
            Location lastLoc = timeline.getInitialLocation();
            double lastSoc = timeline.getInitialSoc();

            for (TimelineEvent event : events) {
                assertThat(event.getStartTime()).isAfterOrEqualTo(lastTime);
                assertThat(event.getStartLocation()).isEqualTo(lastLoc);
                assertThat(event.getStartSoc()).isCloseTo(lastSoc, within(1e-4));
                assertThat(event.getEndSoc()).isGreaterThanOrEqualTo(0.15 - 1e-4);

                lastTime = event.getEndTime();
                lastLoc = event.getEndLocation();
                lastSoc = event.getEndSoc();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section 8 & 9: DistanceMatrix, Fault & Malformed Input Audit
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("8 & 9. DistanceMatrix and Fault Handling Audit")
    class DistanceMatrixAndFaultAudit {

        @Test
        @DisplayName("DistanceMatrix caches results and returns identical distances on repeat queries")
        void distanceMatrixCachingAndSymmetry() {
            double d1 = distanceMatrix.getRoadDistanceKm(LOC_A, LOC_B);
            double d2 = distanceMatrix.getRoadDistanceKm(LOC_A, LOC_B);
            double dReverse = distanceMatrix.getRoadDistanceKm(LOC_B, LOC_A);

            assertThat(d1).isEqualTo(d2);
            assertThat(d1).isCloseTo(dReverse, within(1e-4));
            assertThat(distanceMatrix.cacheSize()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Duplicate trip IDs in planning input are rejected immediately")
        void duplicateTripIdsRejected() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v = createVehicle("EV-1", LOC_A, 0.90, now);
            Trip t1 = createTrip("DUP-1", LOC_A, LOC_B, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));
            Trip t2 = createTrip("DUP-1", LOC_C, LOC_D, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));

            assertThatThrownBy(() -> new PlanningInput("RUN-DUP-TRIP", List.of(v), List.of(t1, t2)))
                    .isInstanceOf(InvalidDomainException.class)
                    .hasMessageContaining("Duplicate trip ID");
        }

        @Test
        @DisplayName("Duplicate vehicle IDs in planning input are rejected immediately")
        void duplicateVehicleIdsRejected() {
            Instant now = Instant.parse("2026-01-01T18:00:00Z");
            Vehicle v1 = createVehicle("EV-DUP", LOC_A, 0.90, now);
            Vehicle v2 = createVehicle("EV-DUP", LOC_B, 0.80, now);
            Trip t = createTrip("T1", LOC_A, LOC_B, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));

            assertThatThrownBy(() -> new PlanningInput("RUN-DUP-VEHICLE", List.of(v1, v2), List.of(t)))
                    .isInstanceOf(InvalidDomainException.class)
                    .hasMessageContaining("Duplicate vehicle ID");
        }

        @Test
        @DisplayName("Duplicate hub IDs in planning input are rejected immediately")
        void duplicateHubIdsRejected() {
            TariffSchedule tariff = createFlatTariff(BigDecimal.TEN);
            ChargerHub h1 = createHub("HUB-DUP", LOC_A, 2, 50.0, tariff);
            ChargerHub h2 = createHub("HUB-DUP", LOC_B, 4, 50.0, tariff);

            assertThatThrownBy(() -> new PlanningInput("RUN-DUP-HUB", List.of(), List.of(), List.of(h1, h2)))
                    .isInstanceOf(InvalidDomainException.class)
                    .hasMessageContaining("Duplicate hub ID");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section 10: Performance & Scalability Audit
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("10. Performance and Scalability Benchmark Audit")
    class ScalabilityAudit {

        @Test
        @DisplayName("Small scale: 10 vehicles, 5 chargers, 50 trips executes in < 500 ms")
        void smallScaleBenchmark() {
            runBenchmark(10, 5, 50, 500);
        }

        @Test
        @DisplayName("Medium scale: 50 vehicles, 10 chargers, 250 trips executes in < 2000 ms")
        void mediumScaleBenchmark() {
            runBenchmark(50, 10, 250, 2000);
        }

        @Test
        @DisplayName("Large scale: 100 vehicles, 20 chargers, 750 trips executes in < 5000 ms")
        void largeScaleBenchmark() {
            runBenchmark(100, 20, 750, 5000);
        }

        @Test
        @DisplayName("Target scale: 300 vehicles, 40 chargers, 1,500 trips executes well within the 30-second budget")
        void targetScaleBenchmark() {
            runBenchmark(300, 40, 1500, 30_000);
        }

        private void runBenchmark(int numVehicles, int numChargers, int numTrips, long maxAllowedMillis) {
            Instant baseTime = Instant.parse("2026-01-01T18:00:00Z");

            // Hubs
            TariffSchedule tariff = createFlatTariff(new BigDecimal("10.00"));
            List<ChargerHub> hubs = List.of(
                    createHub("HUB-1", LOC_A, numChargers / 2, 50.0, tariff),
                    createHub("HUB-2", LOC_B, numChargers - (numChargers / 2), 50.0, tariff)
            );

            // Vehicles
            List<Vehicle> vehicles = new ArrayList<>(numVehicles);
            for (int i = 1; i <= numVehicles; i++) {
                Location startLoc = (i % 2 == 0) ? LOC_A : LOC_B;
                vehicles.add(createVehicle("EV-" + i, startLoc, 0.80, baseTime));
            }

            // Trips staggered across 4 hours
            List<Trip> trips = new ArrayList<>(numTrips);
            for (int i = 1; i <= numTrips; i++) {
                Location pickup = (i % 2 == 0) ? LOC_C : LOC_D;
                Location dest = (i % 2 == 0) ? LOC_D : LOC_C;
                Instant start = baseTime.plusSeconds(900 + (i * 10L));
                Instant finish = start.plusSeconds(3600);
                trips.add(createTrip("TRIP-" + i, pickup, dest, 12.0, start, finish));
            }

            PlanningInput input = new PlanningInput("BENCH-" + numTrips, vehicles, trips, hubs);

            long start = System.currentTimeMillis();
            FleetPlanResult result = planner.plan(input);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
            assertThat(result.getTripAssignments()).hasSize(numTrips);
            assertThat(elapsed)
                    .withFailMessage("Benchmark for %d trips took %d ms, exceeding %d ms limit", numTrips, elapsed, maxAllowedMillis)
                    .isLessThan(maxAllowedMillis);
        }
    }
}
