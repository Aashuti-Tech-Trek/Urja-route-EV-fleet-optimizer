package com.moveinsync.evfleet.validator;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.planner.FeasibilityReason;
import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.PlanningInput;
import com.moveinsync.evfleet.planner.PlanningQualityReport;
import com.moveinsync.evfleet.planner.TimelineEvent;
import com.moveinsync.evfleet.planner.TripAssignment;
import com.moveinsync.evfleet.planner.VehicleTimeline;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorTest {

    private PlanValidator validator;
    private Instant baseTime;
    private Location locHub;
    private Location locPickup;
    private Location locDrop;
    private ChargerHub hub1;
    private Vehicle ev1;

    @BeforeEach
    void setUp() {
        validator = new DefaultPlanValidator();
        baseTime = Instant.parse("2026-10-29T08:00:00Z");

        locHub = new Location(12.9716, 77.5946);
        locPickup = new Location(12.9750, 77.6000);
        locDrop = new Location(12.9800, 77.6100);

        TariffRate standardRate = TariffRate.of(10.00, "INR");
        TariffSchedule schedule = new TariffSchedule(ZoneId.of("Asia/Kolkata"), "INR", List.of(), standardRate);
        hub1 = new ChargerHub("HUB-1", "Central Hub", locHub, 2, 50.0, schedule);

        ev1 = Vehicle.create("EV-1", "TENANT-1", 50.0, 0.20, 0.80, locHub, baseTime);
    }

    private Trip createTrip(String id, Location start, Location end, double distanceKm, Instant startTime, Instant deadline) {
        return new Trip(id, "TENANT-1", start, end, distanceKm, startTime, deadline);
    }

    private FleetPlanResult buildPlan(List<TripAssignment> assignments,
                                      Map<String, VehicleTimeline> timelines,
                                      List<ChargingSession> sessions,
                                      int backupCount,
                                      BigDecimal cost) {
        return new FleetPlanResult(
                "RUN-TEST",
                backupCount == 0 ? FleetPlanResult.PlanStatus.SUCCESS : FleetPlanResult.PlanStatus.PARTIAL_COVERAGE,
                assignments,
                timelines,
                sessions,
                backupCount,
                cost,
                "INR",
                Map.of("HUB-1", cost),
                Duration.ofMillis(50),
                "v2.0",
                "UNVALIDATED",
                new PlanningQualityReport(
                        assignments.size(),
                        assignments.size() - backupCount,
                        backupCount,
                        cost,
                        "INR",
                        sessions.size(),
                        Map.of("HUB-1", sessions.size()),
                        Duration.ofMillis(50),
                        0,
                        assignments.size(),
                        true,
                        0,
                        "Assumptions"
                )
        );
    }

    @Nested
    @DisplayName("1. Battery Reserve & Energy Accounting Verification")
    class BatteryVerificationTests {

        @Test
        @DisplayName("Valid plan with SoC strictly above 15% passes validation")
        void validPlanAboveReservePasses() {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            // 10 km * 0.20 kWh/km = 2.0 kWh; on 50 kWh battery = 0.04 SoC drop (0.80 -> 0.76)
            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300),
                    baseTime.plusSeconds(1800),
                    locHub,
                    locDrop,
                    10.0,
                    2.0,
                    0.80,
                    0.76,
                    "T-1"
            ));

            TripAssignment assignment = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isValid()).isTrue();
            assertThat(report.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("Vehicle ending exactly at 15.00% SoC is valid")
        void exact15PercentSocIsValid() {
            Vehicle evLow = Vehicle.create("EV-LOW", "TENANT-1", 50.0, 0.20, 0.19, locHub, baseTime);

            // 10 km = 2.0 kWh = 0.04 drop -> 0.19 - 0.04 = 0.1500
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(evLow), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-LOW", locHub, baseTime, 0.19, 50.0);
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300),
                    baseTime.plusSeconds(1800),
                    locHub,
                    locDrop,
                    10.0,
                    2.0,
                    0.19,
                    0.1500,
                    "T-1"
            ));

            TripAssignment assignment = TripAssignment.toEv(trip, evLow);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-LOW", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isValid()).isTrue();
        }

        @Test
        @DisplayName("Vehicle dropping slightly below reserve (14.99%) is rejected")
        void socBelowReserveIsRejected() {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300),
                    baseTime.plusSeconds(1800),
                    locHub,
                    locDrop,
                    10.0,
                    2.0,
                    0.80,
                    0.1499, // Breach of 15% floor
                    "T-1"
            ));

            TripAssignment assignment = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.BATTERY_RESERVE_VIOLATION)).isNotEmpty();
        }

        @Test
        @DisplayName("Intermediate event breaching reserve followed by charging is still rejected")
        void intermediateReserveBreachRejectedEvenIfFinalSocIsHigh() {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            TimelineEvent event1 = TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300),
                    baseTime.plusSeconds(1800),
                    locHub,
                    locDrop,
                    10.0,
                    33.0,
                    0.80,
                    0.1400,
                    "T-1"
            );
            TimelineEvent event2 = TimelineEvent.charging(
                    baseTime.plusSeconds(2000),
                    baseTime.plusSeconds(3800),
                    locDrop,
                    33.0,
                    0.1400,
                    0.8000,
                    "CS-1",
                    "HUB-1"
            );
            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0, List.of(event1, event2));

            TripAssignment assignment = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.BATTERY_RESERVE_VIOLATION)).hasSize(1);
        }

        @Test
        @DisplayName("Charging exceeding 100% battery capacity is rejected")
        void chargingExceedingCapacityRejected() {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(3600), baseTime.plusSeconds(5400));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            // Overcharging to 105%
            timeline.addEvent(TimelineEvent.charging(
                    baseTime.plusSeconds(100),
                    baseTime.plusSeconds(1800),
                    locHub,
                    12.5,
                    0.80,
                    1.0500, // > 100%
                    "CS-1",
                    "HUB-1"
            ));

            TripAssignment assignment = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.BATTERY_CAPACITY_EXCEEDED)).isNotEmpty();
        }

        @Test
        @DisplayName("Energy accounting mismatch between reported energy delta and SoC is rejected")
        void energyDeltaMismatchedWithSocRejected() {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            // Reported energy is 2.0 kWh (should result in 0.76 SoC on 50kWh), but plan claims endSoc is 0.79
            timeline.addEvent(new TimelineEvent(
                    TimelineEvent.EventType.PASSENGER_TRIP,
                    baseTime.plusSeconds(300),
                    baseTime.plusSeconds(1800),
                    locHub,
                    locDrop,
                    10.0,
                    -2.0,
                    0.80,
                    0.79, // Fabricated SoC!
                    "T-1",
                    "Fabricated SoC"
            ));

            TripAssignment assignment = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.ENERGY_ACCOUNTING_MISMATCH)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("2. Trip Assignment & Temporal Verification")
    class TripAssignmentTests {

        @Test
        @DisplayName("Trip omitted from plan assignments is rejected as TRIP_UNASSIGNED")
        void omittedTripIsRejected() {
            Trip t1 = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            Trip t2 = createTrip("T-2", locHub, locDrop, 10.0, baseTime.plusSeconds(500), baseTime.plusSeconds(2000));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(t1, t2), List.of(hub1));

            TripAssignment a1 = TripAssignment.toEv(t1, ev1);
            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);

            FleetPlanResult plan = buildPlan(List.of(a1), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);
            ValidationReport report = validator.validate(plan, input);

            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.TRIP_UNASSIGNED)).hasSize(1);
            assertThat(report.getViolationsByCode(ViolationCode.TRIP_UNASSIGNED).get(0).getEntityId()).isEqualTo("T-2");
        }

        @Test
        @DisplayName("Duplicate assignment for same trip is rejected as DUPLICATE_TRIP_ASSIGNMENT")
        void duplicateTripAssignmentRejected() {
            Trip t1 = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(t1), List.of(hub1));

            TripAssignment a1 = TripAssignment.toEv(t1, ev1);
            TripAssignment a2 = TripAssignment.toBackup(t1, FeasibilityReason.NONE, "Duplicate");

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            FleetPlanResult plan = buildPlan(List.of(a1, a2), Map.of("EV-1", timeline), List.of(), 1, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.DUPLICATE_TRIP_ASSIGNMENT)).isNotEmpty();
        }

        @Test
        @DisplayName("Trip finishing after latestAllowedFinishTime is rejected as TRIP_DEADLINE_VIOLATION")
        void tripFinishingAfterDeadlineRejected() {
            Instant deadline = baseTime.plusSeconds(1800);
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), deadline);
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            // Event finishes at +1900, after deadline (+1800)
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300),
                    baseTime.plusSeconds(1900),
                    locHub,
                    locDrop,
                    10.0,
                    2.0,
                    0.80,
                    0.76,
                    "T-1"
            ));

            TripAssignment a1 = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(a1), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.TRIP_DEADLINE_VIOLATION)).isNotEmpty();
        }

        @Test
        @DisplayName("Overlapping events on same vehicle rejected as VEHICLE_TIMELINE_OVERLAP")
        void overlappingTimelineEventsRejected() {
            Trip t1 = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            Trip t2 = createTrip("T-2", locDrop, locHub, 10.0, baseTime.plusSeconds(1500), baseTime.plusSeconds(3000));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(t1, t2), List.of(hub1));

            TimelineEvent e1 = TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300), baseTime.plusSeconds(1800),
                    locHub, locDrop, 10.0, 2.0, 0.80, 0.76, "T-1");

            // Overlaps: starts at 1500 before event 1 ends at 1800
            TimelineEvent e2 = TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(1500), baseTime.plusSeconds(3000),
                    locDrop, locHub, 10.0, 2.0, 0.76, 0.72, "T-2");

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0, List.of(e1, e2));

            TripAssignment a1 = TripAssignment.toEv(t1, ev1);
            TripAssignment a2 = TripAssignment.toEv(t2, ev1);
            FleetPlanResult plan = buildPlan(List.of(a1, a2), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.VEHICLE_TIMELINE_OVERLAP)).isNotEmpty();
        }

        @Test
        @DisplayName("Spatial discontinuity without deadhead travel is rejected as SPATIAL_DISCONTINUITY")
        void spatialDiscontinuityRejected() {
            Trip t1 = createTrip("T-1", locHub, locPickup, 5.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1000));
            Trip t2 = createTrip("T-2", locDrop, locHub, 5.0, baseTime.plusSeconds(1200), baseTime.plusSeconds(2000));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(t1, t2), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300), baseTime.plusSeconds(1000),
                    locHub, locPickup, 5.0, 1.0, 0.80, 0.78, "T-1"));

            // T-2 starts at locDrop, but prior ended at locPickup!
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(1200), baseTime.plusSeconds(2000),
                    locDrop, locHub, 5.0, 1.0, 0.78, 0.76, "T-2"));

            TripAssignment a1 = TripAssignment.toEv(t1, ev1);
            TripAssignment a2 = TripAssignment.toEv(t2, ev1);
            FleetPlanResult plan = buildPlan(List.of(a1, a2), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.SPATIAL_DISCONTINUITY)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("3. Charger Capacity & Concurrency Verification")
    class ChargerConcurrencyTests {

        @Test
        @DisplayName("Concurrent sessions within hub charger count are accepted")
        void concurrentSessionsWithinCapacityAccepted() {
            Vehicle ev2 = Vehicle.create("EV-2", "TENANT-1", 50.0, 0.20, 0.50, locHub, baseTime);

            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1, ev2), List.of(), List.of(hub1));

            ChargingSession s1 = new ChargingSession("CS-1", "EV-1", "HUB-1",
                    baseTime, baseTime.plusSeconds(1800), 0.50, 0.80, 15.0, new BigDecimal("150.00"), "INR");
            ChargingSession s2 = new ChargingSession("CS-2", "EV-2", "HUB-1",
                    baseTime.plusSeconds(300), baseTime.plusSeconds(2100), 0.50, 0.80, 15.0, new BigDecimal("150.00"), "INR");

            VehicleTimeline t1 = new VehicleTimeline("EV-1", locHub, baseTime, 0.50, 50.0);
            t1.addEvent(TimelineEvent.charging(baseTime, baseTime.plusSeconds(1800), locHub, 15.0, 0.50, 0.80, "CS-1", "HUB-1"));

            VehicleTimeline t2 = new VehicleTimeline("EV-2", locHub, baseTime, 0.50, 50.0);
            t2.addEvent(TimelineEvent.charging(baseTime.plusSeconds(300), baseTime.plusSeconds(2100), locHub, 15.0, 0.50, 0.80, "CS-2", "HUB-1"));

            FleetPlanResult plan = buildPlan(List.of(), Map.of("EV-1", t1, "EV-2", t2), List.of(s1, s2), 0, new BigDecimal("300.00"));

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isValid()).isTrue();
        }

        @Test
        @DisplayName("Adjacent sessions [10:00, 10:30) and [10:30, 11:00) on single charger do not overlap")
        void adjacentSessionsOnSingleChargerAccepted() {
            ChargerHub singleChargerHub = new ChargerHub("HUB-SINGLE", "Single", locHub, 1, 50.0, hub1.getTariffSchedule());
            Vehicle ev2 = Vehicle.create("EV-2", "TENANT-1", 50.0, 0.20, 0.50, locHub, baseTime);

            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1, ev2), List.of(), List.of(singleChargerHub));

            Instant t0 = baseTime;
            Instant t1 = baseTime.plusSeconds(1800);
            Instant t2 = baseTime.plusSeconds(3600);

            ChargingSession s1 = new ChargingSession("CS-1", "EV-1", "HUB-SINGLE",
                    t0, t1, 0.50, 0.80, 15.0, new BigDecimal("150.00"), "INR");
            ChargingSession s2 = new ChargingSession("CS-2", "EV-2", "HUB-SINGLE",
                    t1, t2, 0.50, 0.80, 15.0, new BigDecimal("150.00"), "INR");

            VehicleTimeline tl1 = new VehicleTimeline("EV-1", locHub, t0, 0.50, 50.0);
            tl1.addEvent(TimelineEvent.charging(t0, t1, locHub, 15.0, 0.50, 0.80, "CS-1", "HUB-SINGLE"));
            VehicleTimeline tl2 = new VehicleTimeline("EV-2", locHub, t0, 0.50, 50.0);
            tl2.addEvent(TimelineEvent.charging(t1, t2, locHub, 15.0, 0.50, 0.80, "CS-2", "HUB-SINGLE"));

            FleetPlanResult plan = buildPlan(List.of(), Map.of("EV-1", tl1, "EV-2", tl2), List.of(s1, s2), 0, new BigDecimal("300.00"));

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isValid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.CHARGER_CAPACITY_EXCEEDED)).isEmpty();
        }

        @Test
        @DisplayName("Over-subscribed hub sessions exceed capacity and are rejected")
        void oversubscribedHubCapacityRejected() {
            ChargerHub singleChargerHub = new ChargerHub("HUB-SINGLE", "Single", locHub, 1, 50.0, hub1.getTariffSchedule());
            Vehicle ev2 = Vehicle.create("EV-2", "TENANT-1", 50.0, 0.20, 0.50, locHub, baseTime);

            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1, ev2), List.of(), List.of(singleChargerHub));

            Instant t0 = baseTime;
            Instant tOverlap = baseTime.plusSeconds(900); // 15 mins overlap!
            Instant tEnd = baseTime.plusSeconds(1800);

            ChargingSession s1 = new ChargingSession("CS-1", "EV-1", "HUB-SINGLE",
                    t0, tEnd, 0.50, 0.80, 15.0, new BigDecimal("150.00"), "INR");
            ChargingSession s2 = new ChargingSession("CS-2", "EV-2", "HUB-SINGLE",
                    tOverlap, tEnd.plusSeconds(900), 0.50, 0.80, 15.0, new BigDecimal("150.00"), "INR");

            VehicleTimeline tl1 = new VehicleTimeline("EV-1", locHub, t0, 0.50, 50.0);
            tl1.addEvent(TimelineEvent.charging(t0, tEnd, locHub, 15.0, 0.50, 0.80, "CS-1", "HUB-SINGLE"));
            VehicleTimeline tl2 = new VehicleTimeline("EV-2", locHub, t0, 0.50, 50.0);
            tl2.addEvent(TimelineEvent.charging(tOverlap, tEnd.plusSeconds(900), locHub, 15.0, 0.50, 0.80, "CS-2", "HUB-SINGLE"));

            FleetPlanResult plan = buildPlan(List.of(), Map.of("EV-1", tl1, "EV-2", tl2), List.of(s1, s2), 0, new BigDecimal("300.00"));

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.CHARGER_CAPACITY_EXCEEDED)).isNotEmpty();
        }

        @Test
        @DisplayName("Charging scheduled concurrently with passenger trip on same vehicle is rejected")
        void chargingDuringPassengerTripRejected() {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime, baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime, baseTime.plusSeconds(1800),
                    locHub, locDrop, 10.0, 2.0, 0.80, 0.76, "T-1"));

            ChargingSession s1 = new ChargingSession("CS-1", "EV-1", "HUB-1",
                    baseTime.plusSeconds(300), baseTime.plusSeconds(1200),
                    0.76, 0.85, 4.5, new BigDecimal("45.00"), "INR");

            TripAssignment a1 = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(a1), Map.of("EV-1", timeline), List.of(s1), 0, new BigDecimal("45.00"));

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isInvalid()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.CHARGING_DURING_TRIP)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("4. Robustness, Determinism & Concurrency")
    class RobustnessAndDeterminismTests {

        @Test
        @DisplayName("Null plan produces INDETERMINATE with MISSING_VALIDATION_DATA")
        void nullPlanProducesIndeterminate() {
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(), List.of(hub1));
            ValidationReport report = validator.validate(null, input);

            assertThat(report.isIndeterminate()).isTrue();
            assertThat(report.getViolationsByCode(ViolationCode.MISSING_VALIDATION_DATA)).isNotEmpty();
        }

        @Test
        @DisplayName("Empty plan with 0 vehicles and 0 trips produces VALID")
        void emptyInputProducesValid() {
            PlanningInput input = new PlanningInput("RUN-EMPTY", List.of(), List.of(), List.of());
            FleetPlanResult plan = buildPlan(List.of(), Map.of(), List.of(), 0, BigDecimal.ZERO);

            ValidationReport report = validator.validate(plan, input);
            assertThat(report.isValid()).isTrue();
            assertThat(report.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("Repeated validation of identical plan produces deterministic results")
        void determinismVerified() {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300), baseTime.plusSeconds(1800),
                    locHub, locDrop, 10.0, 2.0, 0.80, 0.76, "T-1"));

            TripAssignment assignment = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            ValidationReport r1 = validator.validate(plan, input);
            ValidationReport r2 = validator.validate(plan, input);
            ValidationReport r3 = validator.validate(plan, input);

            assertThat(r1.isValid()).isEqualTo(r2.isValid()).isEqualTo(r3.isValid()).isTrue();
            assertThat(r1.getViolationCount()).isEqualTo(r2.getViolationCount()).isEqualTo(r3.getViolationCount()).isZero();
        }

        @Test
        @DisplayName("Concurrent validation from multiple threads executes cleanly without race conditions")
        void multiThreadedConcurrencyVerified() throws Exception {
            Trip trip = createTrip("T-1", locHub, locDrop, 10.0, baseTime.plusSeconds(300), baseTime.plusSeconds(1800));
            PlanningInput input = new PlanningInput("RUN-1", List.of(ev1), List.of(trip), List.of(hub1));

            VehicleTimeline timeline = new VehicleTimeline("EV-1", locHub, baseTime, 0.80, 50.0);
            timeline.addEvent(TimelineEvent.passengerTrip(
                    baseTime.plusSeconds(300), baseTime.plusSeconds(1800),
                    locHub, locDrop, 10.0, 2.0, 0.80, 0.76, "T-1"));

            TripAssignment assignment = TripAssignment.toEv(trip, ev1);
            FleetPlanResult plan = buildPlan(List.of(assignment), Map.of("EV-1", timeline), List.of(), 0, BigDecimal.ZERO);

            int threads = 10;
            int iterationsPerThread = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger passCount = new AtomicInteger(0);

            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            ValidationReport report = validator.validate(plan, input);
                            if (report.isValid()) {
                                passCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
            pool.shutdown();

            assertThat(passCount.get()).isEqualTo(threads * iterationsPerThread);
        }
    }
}
