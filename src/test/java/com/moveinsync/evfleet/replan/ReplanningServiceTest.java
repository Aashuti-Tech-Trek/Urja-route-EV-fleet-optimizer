package com.moveinsync.evfleet.replan;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.GreedyFleetPlanner;
import com.moveinsync.evfleet.planner.PlanningInput;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.validator.DefaultPlanValidator;
import com.moveinsync.evfleet.validator.PlanValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 5: Live Replanning & Failure Recovery Tests")
class ReplanningServiceTest {

    private ReplanningService replanningService;
    private GreedyFleetPlanner planner;
    private PlanValidator validator;

    private final Instant baseTime = Instant.parse("2026-10-29T08:00:00Z");
    private final Location locDepot = new Location(12.9716, 77.5946);
    private final Location locA = new Location(12.9352, 77.6245);
    private final Location locB = new Location(12.9915, 77.5583);

    @BeforeEach
    void setUp() {
        planner = GreedyFleetPlanner.createDefault();
        validator = new DefaultPlanValidator();
        replanningService = new ReplanningService(planner, validator, Duration.ofMillis(2000));
    }

    private Vehicle createVehicle(String id, double batteryKwh, double soc, Location loc) {
        return Vehicle.create(id, "TENANT-1", batteryKwh, 0.20, soc, loc, baseTime);
    }

    private Trip createTrip(String id, Location p, Location d, double distanceKm, Instant start, Instant deadline) {
        return new Trip(id, "TENANT-1", p, d, distanceKm, start, deadline);
    }

    private ChargerHub createHub(String id, Location loc, int numPlugs, double powerKw) {
        TariffSchedule schedule = new TariffSchedule(
                ZoneId.of("Asia/Kolkata"), "INR", List.of(), TariffRate.of(8.50, "INR"));
        return new ChargerHub(id, "TENANT-1", loc, numPlugs, powerKw, schedule);
    }

    @Nested
    @DisplayName("1. Battery SoC Drift Replanning")
    class BatteryDriftTests {

        @Test
        @DisplayName("No-op drift where battery change is small and schedule remains feasible")
        void shouldHandleNoOpDrift() {
            Vehicle v1 = createVehicle("EV-1", 60.0, 0.90, locDepot);
            Trip t1 = createTrip("TRIP-1", locDepot, locA, 10.0, baseTime.plusSeconds(1800), baseTime.plusSeconds(3600));

            PlanningInput input = new PlanningInput("RUN-1", List.of(v1), List.of(t1), Collections.emptyList());
            FleetPlanResult initialPlan = planner.plan(input);

            // Drift from 0.90 to 0.88 (still ample battery above 15% floor)
            BatteryDriftEvent drift = new BatteryDriftEvent(
                    "EVT-1", "EV-1", 0.88, baseTime.plusSeconds(600));

            ReplanningResult result = replanningService.handleBatteryDrift(input, initialPlan, drift);

            assertThat(result.getOutcome()).isEqualTo(ReplanningOutcome.NO_CHANGE_REQUIRED);
            assertThat(result.getProposedPlan()).isPresent();
            assertThat(result.getPreservedAssignmentsCount()).isEqualTo(1);
            assertThat(result.getChangedAssignmentsCount()).isZero();
        }

        @Test
        @DisplayName("Severe negative drift triggering reschedule or backup cab assignment")
        void shouldHandleSevereNegativeDrift() {
            Vehicle v1 = createVehicle("EV-1", 40.0, 0.50, locDepot);
            Trip t1 = createTrip("TRIP-1", locDepot, locA, 25.0, baseTime.plusSeconds(1800), baseTime.plusSeconds(3600));

            PlanningInput input = new PlanningInput("RUN-2", List.of(v1), List.of(t1), Collections.emptyList());
            FleetPlanResult initialPlan = planner.plan(input);

            // Severe drop to 16% right before trip starts (cannot satisfy 25km trip + 15% reserve)
            BatteryDriftEvent drift = new BatteryDriftEvent(
                    "EVT-2", "EV-1", 0.16, baseTime.plusSeconds(900));

            ReplanningResult result = replanningService.handleBatteryDrift(input, initialPlan, drift);

            assertThat(result.getOutcome()).isEqualTo(ReplanningOutcome.SUCCESS);
            assertThat(result.getProposedPlan()).isPresent();
            assertThat(result.getProposedPlan().get().getTotalBackupTripCount()).isEqualTo(1);
            assertThat(result.getValidationReport()).isPresent();
            assertThat(result.getValidationReport().get().isValid()).isTrue();
        }

        @Test
        @DisplayName("Rejects invalid SoC value (< 0.0 or > 1.0)")
        void shouldRejectInvalidSocValue() {
            assertThatThrownBy(() -> new BatteryDriftEvent("EVT-3", "EV-1", 1.5, baseTime))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new BatteryDriftEvent("EVT-4", "EV-1", -0.1, baseTime))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Stale drift event prior to vehicle availability is cleanly handled")
        void shouldHandleStaleDriftEvent() {
            Vehicle v1 = createVehicle("EV-1", 60.0, 0.90, locDepot);
            Trip t1 = createTrip("TRIP-1", locDepot, locA, 10.0, baseTime.plusSeconds(1800), baseTime.plusSeconds(3600));

            PlanningInput input = new PlanningInput("RUN-3", List.of(v1), List.of(t1), Collections.emptyList());
            FleetPlanResult initialPlan = planner.plan(input);

            // Event timestamp is 1 hour before vehicle was available
            BatteryDriftEvent staleEvent = new BatteryDriftEvent(
                    "EVT-5", "EV-1", 0.80, baseTime.minusSeconds(3600));

            ReplanningResult result = replanningService.handleBatteryDrift(input, initialPlan, staleEvent);
            assertThat(result.getOutcome()).isEqualTo(ReplanningOutcome.STALE_EVENT);
        }
    }

    @Nested
    @DisplayName("2. Charger Failure Replanning")
    class ChargerFailureTests {

        @Test
        @DisplayName("Unrelated charger outage does not disrupt active plan")
        void shouldHandleOutageWithNoScheduledSessions() {
            Vehicle v1 = createVehicle("EV-1", 60.0, 0.90, locDepot);
            Trip t1 = createTrip("TRIP-1", locDepot, locA, 10.0, baseTime.plusSeconds(1800), baseTime.plusSeconds(3600));
            ChargerHub hub1 = createHub("HUB-UNUSED", locDepot, 2, 50.0);

            PlanningInput input = new PlanningInput("RUN-4", List.of(v1), List.of(t1), List.of(hub1));
            FleetPlanResult initialPlan = planner.plan(input);

            ChargerFailureEvent outage = new ChargerFailureEvent(
                    "OUTAGE-1", "HUB-UNUSED", baseTime.plusSeconds(900), baseTime.plusSeconds(7200));

            ReplanningResult result = replanningService.handleChargerFailure(input, initialPlan, outage);

            assertThat(result.getOutcome()).isEqualTo(ReplanningOutcome.NO_CHANGE_REQUIRED);
            assertThat(result.getProposedPlan()).isPresent();
        }

        @Test
        @DisplayName("Charger outage during scheduled session reroutes to operational alternative hub")
        void shouldRerouteToAlternativeHubDuringOutage() {
            Vehicle v1 = createVehicle("EV-1", 50.0, 0.20, locDepot); // needs charging
            Trip t1 = createTrip("TRIP-1", locDepot, locA, 20.0, baseTime.plusSeconds(7200), baseTime.plusSeconds(10800));
            ChargerHub hub1 = createHub("HUB-PRIMARY", locDepot, 2, 50.0);
            ChargerHub hub2 = createHub("HUB-BACKUP", locDepot, 2, 50.0);

            PlanningInput input = new PlanningInput("RUN-5", List.of(v1), List.of(t1), List.of(hub1, hub2));
            FleetPlanResult initialPlan = planner.plan(input);

            // Hub-1 fails
            ChargerFailureEvent outage = new ChargerFailureEvent(
                    "OUTAGE-2", "HUB-PRIMARY", baseTime, baseTime.plusSeconds(14400));

            ReplanningResult result = replanningService.handleChargerFailure(input, initialPlan, outage);

            assertThat(result.getOutcome()).isEqualTo(ReplanningOutcome.SUCCESS);
            assertThat(result.getProposedPlan()).isPresent();
            assertThat(result.getValidationReport()).isPresent();
            assertThat(result.getValidationReport().get().isValid()).isTrue();

            // Verify sessions only use operational hubs
            result.getProposedPlan().get().getChargingSessions().forEach(s ->
                    assertThat(s.getHubId()).isNotEqualTo("HUB-PRIMARY"));
        }
    }

    @Nested
    @DisplayName("3. Time Budget & Determinism")
    class BudgetAndDeterminismTests {

        @Test
        @DisplayName("Replanning runs within 2.0s time budget for standard fleet operations")
        void shouldExecuteWithinBudget() {
            Vehicle v1 = createVehicle("EV-1", 60.0, 0.80, locDepot);
            Trip t1 = createTrip("TRIP-1", locDepot, locA, 10.0, baseTime.plusSeconds(1800), baseTime.plusSeconds(3600));

            PlanningInput input = new PlanningInput("RUN-6", List.of(v1), List.of(t1), Collections.emptyList());
            FleetPlanResult initialPlan = planner.plan(input);

            BatteryDriftEvent drift = new BatteryDriftEvent(
                    "EVT-6", "EV-1", 0.75, baseTime.plusSeconds(600));

            ReplanningResult result = replanningService.handleBatteryDrift(input, initialPlan, drift);

            assertThat(result.getElapsedDuration().toMillis()).isLessThan(2000);
            assertThat(result.isTimeBudgetExceeded()).isFalse();
        }
    }
}
