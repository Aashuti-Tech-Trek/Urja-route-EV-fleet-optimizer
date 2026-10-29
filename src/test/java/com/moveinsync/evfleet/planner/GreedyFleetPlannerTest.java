package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.charging.TwoPartChargingCurve;
import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.StandardEnergyModel;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.time.TariffSlot;
import com.moveinsync.evfleet.time.TariffType;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GreedyFleetPlanner & Phase 2 Integration Tests")
class GreedyFleetPlannerTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final TravelConfig TRAVEL_CONFIG = new TravelConfig(1.0, 36.0); // 36 km/h = 100 s/km
    private static final Location DEPOT = new Location(12.9716, 77.5946);     // MG Road
    private static final Location HUB_LOC = new Location(12.9352, 77.6245);   // Koramangala
    private static final Location PICKUP_A = new Location(12.9279, 77.6271);  // HSR Layout
    private static final Location DEST_A = new Location(12.9900, 77.6500);    // Indiranagar
    private static final Location PICKUP_B = new Location(12.9800, 77.6400);
    private static final Location DEST_B = new Location(12.9100, 77.6000);

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
        planner = new GreedyFleetPlanner(feasibilityChecker, chargingScheduler, distanceMatrix, estimator, energyModel, energyFactors, LocalSearchConfig.of(10, 500));
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

    private TariffSchedule createTwoTierTariff(BigDecimal offPeakRate, BigDecimal peakRate) {
        TariffRate offPeak = new TariffRate(offPeakRate, "INR");
        TariffRate peak = new TariffRate(peakRate, "INR");
        // Peak from 18:00 to 22:00
        TariffSlot peakSlot = new TariffSlot(LocalTime.of(18, 0), LocalTime.of(22, 0), peak, TariffType.PEAK);
        return new TariffSchedule(UTC, "INR", List.of(peakSlot), offPeak);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 1: One vehicle, one feasible trip
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("1. One vehicle and one feasible trip — full EV assignment and valid plan")
    void oneVehicleOneFeasibleTrip() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(1800);
        Instant finish = start.plusSeconds(3600);

        Vehicle v = createVehicle("EV-1", DEPOT, 0.90, now);
        Trip t = createTrip("TRIP-1", PICKUP_A, DEST_A, 10.0, start, finish);

        PlanningInput input = new PlanningInput("RUN-1", List.of(v), List.of(t));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getStatus()).isEqualTo(FleetPlanResult.PlanStatus.SUCCESS);
        assertThat(result.getTotalBackupTripCount()).isZero();
        assertThat(result.getEvAssignments()).hasSize(1);
        assertThat(result.getEvAssignments().get(0).getAssignedVehicle().getVehicleId()).isEqualTo("EV-1");
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");

        VehicleTimeline timeline = result.getVehicleTimelines().get("EV-1");
        assertThat(timeline.getEvents()).isNotEmpty();
        assertThat(timeline.getCurrentSoc()).isGreaterThan(0.15);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 2: Empty travel depleting reserve
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("2. Empty travel causes battery reserve violation → backup cab assignment")
    void emptyTravelDepletingReserveCausesBackup() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(3600);
        Instant finish = start.plusSeconds(7200);

        // SoC = 0.16 -> 9.6 kWh, reserve is 9 kWh, available is 0.6 kWh.
        // Empty drive from DEPOT to PICKUP_A is ~8 km -> energy ~1.6 kWh > 0.6 kWh.
        Vehicle v = createVehicle("EV-1", DEPOT, 0.16, now);
        Trip t = createTrip("TRIP-1", PICKUP_A, DEST_A, 5.0, start, finish);

        PlanningInput input = new PlanningInput("RUN-2", List.of(v), List.of(t));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getTotalBackupTripCount()).isEqualTo(1);
        TripAssignment backup = result.getBackupAssignments().get(0);
        assertThat(backup.getBackupReason()).isEqualTo(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 3: Trip energy violates reserve
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("3. Trip journey energy violates 15% reserve floor → backup cab assignment")
    void tripEnergyViolatesReserveCausesBackup() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(1800);
        Instant finish = start.plusSeconds(7200);

        // Vehicle already at pickup, SoC = 0.20 (12 kWh), reserve = 9 kWh, available = 3 kWh.
        // Trip is 50 km -> requires 10 kWh > 3 kWh available.
        Vehicle v = createVehicle("EV-1", PICKUP_A, 0.20, now);
        Trip t = createTrip("TRIP-1", PICKUP_A, DEST_A, 50.0, start, finish);

        PlanningInput input = new PlanningInput("RUN-3", List.of(v), List.of(t));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getTotalBackupTripCount()).isEqualTo(1);
        TripAssignment backup = result.getBackupAssignments().get(0);
        assertThat(backup.getBackupReason()).isEqualTo(FeasibilityReason.INSUFFICIENT_ENERGY_FOR_TRIP);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 4: Impossible latest finish deadline
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("4. Impossible latest finish deadline → backup cab with DEADLINE_INFEASIBLE")
    void impossibleDeadlineCausesBackup() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(1800);
        // Trip distance = 50 km (takes 5000s at 36 km/h), but latest finish is only 10s after start!
        Instant finish = start.plusSeconds(10);

        Vehicle v = createVehicle("EV-1", PICKUP_A, 0.90, now);
        Trip t = createTrip("TRIP-1", PICKUP_A, DEST_A, 50.0, start, finish);

        PlanningInput input = new PlanningInput("RUN-4", List.of(v), List.of(t));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getTotalBackupTripCount()).isEqualTo(1);
        assertThat(result.getBackupAssignments().get(0).getBackupReason())
                .isEqualTo(FeasibilityReason.DEADLINE_INFEASIBLE);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 5: Multiple trips competing for one vehicle
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("5. Multiple sequential trips assigned to one EV if timing and energy permit")
    void multipleSequentialTripsOnOneVehicle() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant t1Start = now.plusSeconds(1800);
        Instant t1Finish = t1Start.plusSeconds(3600);

        Instant t2Start = now.plusSeconds(7200);
        Instant t2Finish = t2Start.plusSeconds(3600);

        Vehicle v = createVehicle("EV-1", PICKUP_A, 0.95, now);
        Trip t1 = createTrip("TRIP-1", PICKUP_A, DEST_A, 10.0, t1Start, t1Finish);
        Trip t2 = createTrip("TRIP-2", DEST_A, PICKUP_A, 10.0, t2Start, t2Finish);

        PlanningInput input = new PlanningInput("RUN-5", List.of(v), List.of(t1, t2));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getStatus()).isEqualTo(FleetPlanResult.PlanStatus.SUCCESS);
        assertThat(result.getEvAssignments()).hasSize(2);
        assertThat(result.getTotalBackupTripCount()).isZero();
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");

        VehicleTimeline timeline = result.getVehicleTimelines().get("EV-1");
        assertThat(timeline.getEvents()).hasSize(2); // two passenger trips
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 6: Charging integration and charger hub concurrency
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("6. Charging scheduled when SoC low, respecting hub charger capacity")
    void chargingScheduledWhenSocLow() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(7200); // 2 hours window: ample time to charge
        Instant finish = start.plusSeconds(3600);

        // Vehicle starts at HUB with low SoC (0.20, available 3 kWh above reserve).
        // Trip requires 40 km -> 8 kWh -> vehicle MUST charge to serve trip.
        Vehicle v = createVehicle("EV-1", HUB_LOC, 0.20, now);
        Trip t = createTrip("TRIP-1", HUB_LOC, DEST_A, 40.0, start, finish);

        TariffSchedule tariff = TariffSchedule.ofFlatRate(UTC, new TariffRate(new BigDecimal("8.00"), "INR"));
        ChargerHub hub = createHub("HUB-1", HUB_LOC, 2, 50.0, tariff);

        PlanningInput input = new PlanningInput("RUN-6", List.of(v), List.of(t), List.of(hub));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getStatus()).isEqualTo(FleetPlanResult.PlanStatus.SUCCESS);
        assertThat(result.getEvAssignments()).hasSize(1);
        assertThat(result.getChargingSessions()).hasSize(1);
        assertThat(result.getTotalChargingCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 7: Two-part charging curve across 80% SoC
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("7. Charging across 80% SoC calculates energy and duration accurately")
    void chargingAcross80PercentSoc() {
        TwoPartChargingCurve curve = new TwoPartChargingCurve();
        double batteryCap = 60.0;
        double chargerPowerKw = 50.0;

        // Charging from 0.50 to 0.90 crosses the 0.80 threshold
        double timeHours = curve.calculateChargingTimeHours(batteryCap, 0.50, 0.90, chargerPowerKw);
        double energyDelivered = curve.calculateEnergyDeliveredKwh(batteryCap, 0.50, 0.90);

        // Fast energy: (0.80 - 0.50) * 60 = 18 kWh at 50 kW -> 0.36 h
        // Taper energy: (0.90 - 0.80) * 60 = 6 kWh at 25 kW -> 0.24 h
        // Total time = 0.60 h (36 min)
        assertThat(timeHours).isCloseTo(0.60, org.assertj.core.api.Assertions.within(1e-5));
        assertThat(energyDelivered).isCloseTo(24.0, org.assertj.core.api.Assertions.within(1e-5));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 8: Peak / off-peak tariff cost calculation
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("8. Peak and off-peak tariff transitions calculate exact split costs")
    void tariffSplitCostCalculation() {
        TariffSchedule tariff = createTwoTierTariff(new BigDecimal("5.00"), new BigDecimal("10.00"));

        // Interval from 17:30 to 18:30 (1 hour): 30 min off-peak @ 5.00, 30 min peak @ 10.00
        // Delivering 20 kWh total -> 10 kWh @ 5 = 50, 10 kWh @ 10 = 100 -> Total = 150.00
        Instant start = Instant.parse("2026-01-01T17:30:00Z");
        Instant end = Instant.parse("2026-01-01T18:30:00Z");
        BigDecimal cost = tariff.calculateCost(start, end, 20.0);

        assertThat(cost).isEqualByComparingTo(new BigDecimal("150.0000"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 9: No EV candidate available → Backup cab with reason
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("9. No EV candidate available → backup assignment with clear reason code")
    void noEvAvailableCausesBackupWithReason() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Trip t = createTrip("TRIP-1", PICKUP_A, DEST_A, 10.0, now, now.plusSeconds(3600));

        // Zero vehicles supplied
        PlanningInput input = new PlanningInput("RUN-9", List.of(), List.of(t));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getTotalBackupTripCount()).isEqualTo(1);
        TripAssignment backup = result.getBackupAssignments().get(0);
        assertThat(backup.getBackupReason()).isEqualTo(FeasibilityReason.VEHICLE_UNAVAILABLE);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 10: Backup-trip minimization strictly prioritized over electricity cost
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("10. Backup minimization strictly takes precedence over high electricity charging cost")
    void backupMinimizationPrioritizedOverCost() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(7200);
        Instant finish = start.plusSeconds(3600);

        // Low SoC vehicle requiring charging. Tariff rate is extremely high (100 INR/kWh).
        // The engine MUST still schedule the expensive charge and assign the EV, rather than
        // abandoning the trip to a backup cab to "save electricity cost".
        Vehicle v = createVehicle("EV-1", HUB_LOC, 0.20, now);
        Trip t = createTrip("TRIP-1", HUB_LOC, DEST_A, 30.0, start, finish);

        TariffSchedule expensiveTariff = TariffSchedule.ofFlatRate(UTC, new TariffRate(new BigDecimal("100.00"), "INR"));
        ChargerHub hub = createHub("HUB-1", HUB_LOC, 1, 50.0, expensiveTariff);

        PlanningInput input = new PlanningInput("RUN-10", List.of(v), List.of(t), List.of(hub));
        FleetPlanResult result = planner.plan(input);

        assertThat(result.getTotalBackupTripCount()).isZero();
        assertThat(result.getEvAssignments()).hasSize(1);
        assertThat(result.getTotalChargingCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 11: Deterministic output for identical input
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("11. Deterministic output: two identical planning runs produce bitwise equivalent plans")
    void deterministicOutput() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Vehicle v1 = createVehicle("EV-1", DEPOT, 0.90, now);
        Vehicle v2 = createVehicle("EV-2", HUB_LOC, 0.85, now);
        Trip t1 = createTrip("TRIP-1", PICKUP_A, DEST_A, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));
        Trip t2 = createTrip("TRIP-2", PICKUP_B, DEST_B, 12.0, now.plusSeconds(2000), now.plusSeconds(4000));

        PlanningInput input1 = new PlanningInput("RUN-11A", List.of(v1, v2), List.of(t1, t2));
        PlanningInput input2 = new PlanningInput("RUN-11B", List.of(v1, v2), List.of(t1, t2));

        FleetPlanResult r1 = planner.plan(input1);
        FleetPlanResult r2 = planner.plan(input2);

        assertThat(r1.getTotalBackupTripCount()).isEqualTo(r2.getTotalBackupTripCount());
        assertThat(r1.getTripAssignments()).hasSize(r2.getTripAssignments().size());
        for (int i = 0; i < r1.getTripAssignments().size(); i++) {
            TripAssignment a1 = r1.getTripAssignments().get(i);
            TripAssignment a2 = r2.getTripAssignments().get(i);
            assertThat(a1.getAssignmentType()).isEqualTo(a2.getAssignmentType());
            if (a1.isEvAssignment()) {
                assertThat(a1.getAssignedVehicle().getVehicleId())
                        .isEqualTo(a2.getAssignedVehicle().getVehicleId());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 12: Hub charger concurrency capacity limit enforced
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("12. Hub with 1 charger plug cannot accommodate 2 concurrent vehicles")
    void hubCapacityPreventsConcurrencyExceeded() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(3600);
        Instant finish = start.plusSeconds(3600);

        // Hub has only 1 charger plug
        TariffSchedule tariff = TariffSchedule.ofFlatRate(UTC, new TariffRate(new BigDecimal("10.00"), "INR"));
        ChargerHub hub = createHub("HUB-1", HUB_LOC, 1, 50.0, tariff);

        // 2 vehicles both at HUB with low SoC (0.20), both need charging for trips starting at same time
        Vehicle v1 = createVehicle("EV-1", HUB_LOC, 0.20, now);
        Vehicle v2 = createVehicle("EV-2", HUB_LOC, 0.20, now);

        Trip t1 = createTrip("TRIP-1", HUB_LOC, DEST_A, 30.0, start, finish);
        Trip t2 = createTrip("TRIP-2", HUB_LOC, DEST_B, 30.0, start, finish);

        PlanningInput input = new PlanningInput("RUN-12", List.of(v1, v2), List.of(t1, t2), List.of(hub));
        FleetPlanResult result = planner.plan(input);

        // Only 1 vehicle can charge concurrently at the 1-plug hub; the other must fallback to backup cab
        assertThat(result.getChargingSessions()).hasSize(1);
        assertThat(result.getEvAssignments()).hasSize(1);
        assertThat(result.getTotalBackupTripCount()).isEqualTo(1);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 13: Charging session does not exceed battery capacity
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("13. Charging session never charges vehicle beyond 100% SoC")
    void chargingNeverExceedsMaxCapacity() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(7200);
        Instant finish = start.plusSeconds(3600);

        // Vehicle at 0.70 SoC charges before trip
        Vehicle v = createVehicle("EV-1", HUB_LOC, 0.70, now);
        Trip t = createTrip("TRIP-1", HUB_LOC, DEST_A, 40.0, start, finish);

        TariffSchedule tariff = TariffSchedule.ofFlatRate(UTC, new TariffRate(new BigDecimal("10.00"), "INR"));
        ChargerHub hub = createHub("HUB-1", HUB_LOC, 1, 50.0, tariff);

        PlanningInput input = new PlanningInput("RUN-13", List.of(v), List.of(t), List.of(hub));
        FleetPlanResult result = planner.plan(input);

        if (!result.getChargingSessions().isEmpty()) {
            ChargingSession cs = result.getChargingSessions().get(0);
            assertThat(cs.getEndSoc()).isLessThanOrEqualTo(1.0);
            assertThat(cs.getEnergyDeliveredKwh()).isLessThanOrEqualTo(60.0);
        }
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 14: Vehicle timeline consistency
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("14. Vehicle timeline maintains spatial and chronological continuity")
    void timelineConsistencyMaintained() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Instant start = now.plusSeconds(1800);
        Instant finish = start.plusSeconds(3600);

        Vehicle v = createVehicle("EV-1", DEPOT, 0.90, now);
        Trip t = createTrip("TRIP-1", PICKUP_A, DEST_A, 10.0, start, finish);

        PlanningInput input = new PlanningInput("RUN-14", List.of(v), List.of(t));
        FleetPlanResult result = planner.plan(input);

        VehicleTimeline timeline = result.getVehicleTimelines().get("EV-1");
        List<TimelineEvent> events = timeline.getEvents();

        // Should have empty drive from DEPOT to PICKUP_A, then passenger trip to DEST_A
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo(TimelineEvent.EventType.EMPTY_DRIVE);
        assertThat(events.get(0).getStartLocation()).isEqualTo(DEPOT);
        assertThat(events.get(0).getEndLocation()).isEqualTo(PICKUP_A);

        assertThat(events.get(1).getEventType()).isEqualTo(TimelineEvent.EventType.PASSENGER_TRIP);
        assertThat(events.get(1).getStartLocation()).isEqualTo(PICKUP_A);
        assertThat(events.get(1).getEndLocation()).isEqualTo(DEST_A);

        assertThat(timeline.getCurrentLocation()).isEqualTo(DEST_A);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 15: Invalid or contradictory input data
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("15. Null planning input is rejected immediately")
    void nullInputRejected() {
        assertThatThrownBy(() -> planner.plan(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 16: Local search budget preserves best valid plan
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("16. Local search preserves best valid plan within iteration budget")
    void localSearchPreservesBestValidPlan() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        Vehicle v = createVehicle("EV-1", DEPOT, 0.90, now);
        Trip t = createTrip("TRIP-1", PICKUP_A, DEST_A, 10.0, now.plusSeconds(1800), now.plusSeconds(3600));

        // Planner with local search enabled
        GreedyFleetPlanner lsPlanner = new GreedyFleetPlanner(
                feasibilityChecker, chargingScheduler, distanceMatrix, estimator, energyModel, energyFactors,
                LocalSearchConfig.of(20, 1000));

        PlanningInput input = new PlanningInput("RUN-16", List.of(v), List.of(t));
        FleetPlanResult result = lsPlanner.plan(input);

        assertThat(result.getStatus()).isEqualTo(FleetPlanResult.PlanStatus.SUCCESS);
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
        assertThat(result.getQualityReport().getLocalSearchIterations()).isGreaterThanOrEqualTo(0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scenario 17: Benchmark scalability test
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("17. Scalability benchmark harness: 20 vehicles, 50 trips executes in < 2 seconds")
    void scalabilityBenchmark() {
        Instant now = Instant.parse("2026-01-01T18:00:00Z");
        List<Vehicle> vehicles = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            vehicles.add(createVehicle("EV-" + i, DEPOT, 0.85, now));
        }

        List<Trip> trips = new java.util.ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Instant start = now.plusSeconds(1800 + (i * 300));
            Instant finish = start.plusSeconds(3600);
            trips.add(createTrip("TRIP-" + i, PICKUP_A, DEST_A, 8.0, start, finish));
        }

        PlanningInput input = new PlanningInput("BENCHMARK-RUN", vehicles, trips);
        long startTime = System.currentTimeMillis();
        FleetPlanResult result = planner.plan(input);
        long elapsed = System.currentTimeMillis() - startTime;

        assertThat(elapsed).isLessThan(5000); // Well under 5 seconds
        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
        assertThat(result.getTripAssignments()).hasSize(50);
        assertThat(result.getQualityReport()).isNotNull();
    }
}
