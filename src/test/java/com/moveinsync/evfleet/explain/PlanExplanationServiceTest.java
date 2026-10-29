package com.moveinsync.evfleet.explain;

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
import com.moveinsync.evfleet.validator.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 4: Plan Explainability, Cost Breakdown, Quality Metrics & Lower Bound Analysis Tests")
class PlanExplanationServiceTest {

    private PlanExplanationService explanationService;
    private PlanValidator validator;
    private GreedyFleetPlanner planner;

    private final Instant baseTime = Instant.parse("2026-10-29T08:00:00Z");
    private final Location locHub = new Location(12.9716, 77.5946);
    private final Location locA = new Location(12.9352, 77.6245);
    private final Location locB = new Location(12.9915, 77.5583);

    @BeforeEach
    void setUp() {
        explanationService = new PlanExplanationService();
        validator = new DefaultPlanValidator();
        planner = GreedyFleetPlanner.createDefault();
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
    @DisplayName("1. Basic Plan Explanation Generation")
    class BasicExplanationTests {

        @Test
        @DisplayName("Should generate explanation for a feasible plan with EV assignments")
        void shouldExplainFeasiblePlan() {
            Vehicle v1 = createVehicle("EV-1", 60.0, 0.90, locHub);
            Trip t1 = createTrip("TRIP-1", locHub, locA, 10.0, baseTime.plusSeconds(900), baseTime.plusSeconds(3600));
            ChargerHub hub = createHub("HUB-1", locHub, 2, 50.0);

            PlanningInput input = new PlanningInput("RUN-1", List.of(v1), List.of(t1), List.of(hub));
            FleetPlanResult plan = planner.plan(input);
            ValidationReport report = validator.validate(plan, input);

            PlanExplanation explanation = explanationService.explain(input, plan, report);

            assertThat(explanation).isNotNull();
            assertThat(explanation.getPlanningRunId()).isEqualTo(plan.getPlanningRunId());
            assertThat(explanation.getAssignmentExplanations()).hasSize(1);

            AssignmentExplanation assignExpl = explanation.getAssignmentExplanations().get(0);
            assertThat(assignExpl.getTripId()).isEqualTo("TRIP-1");
            assertThat(assignExpl.getDecisionType()).isEqualTo(AssignmentExplanation.DecisionType.EV_ASSIGNED);
            assertThat(assignExpl.getAssignedVehicleId()).contains("EV-1");
            assertThat(assignExpl.getSelectionRationale()).isNotBlank();

            CostBreakdown costs = explanation.getCostBreakdown();
            assertThat(costs).isNotNull();
            assertThat(costs.getCurrency()).isEqualTo("INR");

            PlanQualityReport quality = explanation.getQualityReport();
            assertThat(quality).isNotNull();
            assertThat(quality.getTotalTripsRequested()).isEqualTo(1);
            assertThat(quality.getEvAssignedTrips()).isEqualTo(1);
            assertThat(quality.getBackupAssignedTrips()).isZero();
            assertThat(quality.getEvCoveragePercentage()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Should explain backup decisions when vehicle battery or timing cannot satisfy trip")
        void shouldExplainBackupDecision() {
            // Vehicle has low battery (SoC below reserve floor)
            Vehicle v1 = createVehicle("EV-LOW", 60.0, 0.10, locHub);
            Trip t1 = createTrip("TRIP-URGENT", locA, locB, 15.0, baseTime.plusSeconds(300), baseTime.plusSeconds(600));

            PlanningInput input = new PlanningInput("RUN-2", List.of(v1), List.of(t1), Collections.emptyList());
            FleetPlanResult plan = planner.plan(input);
            PlanExplanation explanation = explanationService.explain(input, plan, null);

            assertThat(explanation.getAssignmentExplanations()).hasSize(1);
            AssignmentExplanation assignExpl = explanation.getAssignmentExplanations().get(0);
            assertThat(assignExpl.getDecisionType()).isEqualTo(AssignmentExplanation.DecisionType.BACKUP_ASSIGNED);
            assertThat(assignExpl.getAssignedVehicleId()).isEmpty();
            assertThat(assignExpl.getPrimaryRejectionReason()).isPresent();
            assertThat(assignExpl.getAlternatives()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("2. Cost Breakdown & Subtotals")
    class CostBreakdownTests {

        @Test
        @DisplayName("Should calculate electricity and backup fleet costs accurately")
        void shouldCalculateCostsCorrectly() {
            Vehicle v1 = createVehicle("EV-1", 50.0, 0.20, locHub);
            Trip t1 = createTrip("TRIP-1", locA, locB, 12.0, baseTime.plusSeconds(3600), baseTime.plusSeconds(7200));
            ChargerHub hub = createHub("HUB-CENTRAL", locHub, 2, 60.0);

            PlanningInput input = new PlanningInput("RUN-3", List.of(v1), List.of(t1), List.of(hub));
            FleetPlanResult plan = planner.plan(input);
            PlanExplanation explanation = explanationService.explain(input, plan, null);

            CostBreakdown cb = explanation.getCostBreakdown();
            assertThat(cb.getTotalElectricityCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(cb.getBackupCabCostNote()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("3. Lower Bound Analysis")
    class LowerBoundAnalysisTests {

        @Test
        @DisplayName("Backup trip count lower bound should be <= actual backup trips")
        void backupLowerBoundShouldBeSound() {
            Vehicle v1 = createVehicle("EV-1", 40.0, 0.50, locHub);
            Trip t1 = createTrip("TRIP-1", locHub, locA, 10.0, baseTime.plusSeconds(600), baseTime.plusSeconds(1800));
            Trip t2 = createTrip("TRIP-2", locHub, locB, 10.0, baseTime.plusSeconds(600), baseTime.plusSeconds(1800));

            PlanningInput input = new PlanningInput("RUN-4", List.of(v1), List.of(t1, t2), Collections.emptyList());
            FleetPlanResult plan = planner.plan(input);
            PlanExplanation explanation = explanationService.explain(input, plan, null);

            LowerBoundResult lb = explanation.getBackupTripLowerBound();
            assertThat(lb.getStatus()).isEqualTo(LowerBoundStatus.WEAK);
            assertThat(lb.getLowerBoundValue()).isPresent();
            assertThat(lb.getActualValue()).isPresent();
            assertThat(lb.getLowerBoundValue().get()).isLessThanOrEqualTo(lb.getActualValue().get());
            assertThat(lb.getLowerBoundValue().get().intValue()).isZero();
        }

        @Test
        @DisplayName("Electricity cost lower bound should be sound")
        void electricityCostLowerBoundShouldBeSound() {
            Vehicle v1 = createVehicle("EV-1", 60.0, 0.80, locHub);
            Trip t1 = createTrip("TRIP-1", locHub, locA, 10.0, baseTime.plusSeconds(900), baseTime.plusSeconds(2700));
            ChargerHub hub = createHub("HUB-1", locHub, 2, 50.0);

            PlanningInput input = new PlanningInput("RUN-5", List.of(v1), List.of(t1), List.of(hub));
            FleetPlanResult plan = planner.plan(input);
            PlanExplanation explanation = explanationService.explain(input, plan, null);

            LowerBoundResult lb = explanation.getElectricityCostLowerBound();
            assertThat(lb.getStatus()).isEqualTo(LowerBoundStatus.AVAILABLE);
            assertThat(lb.getLowerBoundValue()).isPresent();
            assertThat(lb.getActualValue()).isPresent();
            assertThat(lb.getLowerBoundValue().get()).isLessThanOrEqualTo(lb.getActualValue().get());
        }
    }

    @Nested
    @DisplayName("4. Plan Quality Report & Invariants")
    class PlanQualityReportTests {

        @Test
        @DisplayName("Validation failure status is faithfully represented without modifying validator findings")
        void shouldFaithfullyReportValidationStatus() {
            Vehicle v1 = createVehicle("EV-1", 60.0, 0.90, locHub);
            Trip t1 = createTrip("TRIP-1", locHub, locA, 10.0, baseTime.plusSeconds(900), baseTime.plusSeconds(2700));

            PlanningInput input = new PlanningInput("RUN-6", List.of(v1), List.of(t1), Collections.emptyList());
            FleetPlanResult plan = planner.plan(input);
            ValidationReport validReport = validator.validate(plan, input);

            PlanExplanation explanation = explanationService.explain(input, plan, validReport);
            PlanQualityReport qr = explanation.getQualityReport();

            assertThat(qr.getValidationStatus()).isEqualTo(validReport.getStatus());
            assertThat(qr.getViolationCount()).isEqualTo(validReport.getViolations().size());
        }

        @Test
        @DisplayName("Null input or plan should throw NullPointerException")
        void shouldRejectNullArguments() {
            assertThatThrownBy(() -> explanationService.explain(null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
