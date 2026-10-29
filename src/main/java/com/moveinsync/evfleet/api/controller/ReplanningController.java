package com.moveinsync.evfleet.api.controller;

import com.moveinsync.evfleet.api.dto.BatteryDriftRequest;
import com.moveinsync.evfleet.api.dto.ChargerFailureRequest;
import com.moveinsync.evfleet.api.dto.ReplanningApiResponse;
import com.moveinsync.evfleet.domain.*;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.persistence.entity.ChargerHubEntity;
import com.moveinsync.evfleet.persistence.entity.TripEntity;
import com.moveinsync.evfleet.persistence.entity.VehicleEntity;
import com.moveinsync.evfleet.persistence.service.FleetPersistenceService;
import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.GreedyFleetPlanner;
import com.moveinsync.evfleet.planner.PlanningInput;
import com.moveinsync.evfleet.replan.*;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/replanning")
@Tag(name = "Replanning", description = "Live Replanning and Disruption Recovery")
public class ReplanningController {

    private final FleetPersistenceService fleetService;
    private final ReplanningService replanningService;

    public ReplanningController(FleetPersistenceService fleetService) {
        this.fleetService = fleetService;
        this.replanningService = new ReplanningService();
    }

    @PostMapping("/battery-drift")
    @Operation(summary = "Handle real-time battery drift event under 2.0s SLA")
    public ResponseEntity<ReplanningApiResponse> handleBatteryDrift(
            @RequestHeader(name = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody BatteryDriftRequest request) {

        PlanningInput input = buildDefaultInput(tenantId);
        FleetPlanResult currentPlan = GreedyFleetPlanner.createFastReplanner().plan(input);

        BatteryDriftEvent event = new BatteryDriftEvent(
            UUID.randomUUID().toString(),
            request.vehicleId(),
            request.observedSoc(),
            request.eventTime()
        );

        ReplanningResult result = replanningService.handleBatteryDrift(
            input,
            currentPlan,
            event
        );

        return ResponseEntity.ok(toApiResponse(result));
    }

    @PostMapping("/charger-failure")
    @Operation(summary = "Handle charger hub outage event under 2.0s SLA")
    public ResponseEntity<ReplanningApiResponse> handleChargerFailure(
            @RequestHeader(name = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody ChargerFailureRequest request) {

        PlanningInput input = buildDefaultInput(tenantId);
        FleetPlanResult currentPlan = GreedyFleetPlanner.createFastReplanner().plan(input);

        ChargerFailureEvent event = new ChargerFailureEvent(
            UUID.randomUUID().toString(),
            request.hubId(),
            request.outageStart(),
            request.estimatedRestoration()
        );

        ReplanningResult result = replanningService.handleChargerFailure(
            input,
            currentPlan,
            event
        );

        return ResponseEntity.ok(toApiResponse(result));
    }

    private ReplanningApiResponse toApiResponse(ReplanningResult result) {
        String proposalId = UUID.randomUUID().toString();
        double cost = result.getProposedPlan().map(p -> p.getTotalChargingCost().doubleValue()).orElse(0.0);
        double delta = result.getCostAfter().subtract(result.getCostBefore()).doubleValue();
        boolean valid = result.getValidationReport().map(r -> r.isValid()).orElse(false);

        return new ReplanningApiResponse(
            result.getOutcome().name(),
            proposalId,
            cost,
            delta,
            List.of("changedCount=" + result.getChangedAssignmentsCount()),
            List.of("preservedCount=" + result.getPreservedAssignmentsCount()),
            result.getElapsedDuration().toMillis(),
            valid
        );
    }

    private PlanningInput buildDefaultInput(String tenantId) {
        List<VehicleEntity> vEntities = fleetService.getVehicles(tenantId);
        List<TripEntity> tEntities = fleetService.getTrips(tenantId);
        List<ChargerHubEntity> hEntities = fleetService.getHubs(tenantId);

        List<Vehicle> vehicles = vEntities.stream().map(ve ->
            Vehicle.create(
                ve.getId(),
                tenantId,
                ve.getBatteryCapacityKwh(),
                ve.getEfficiencyKwhPerKm(),
                ve.getCurrentSoc(),
                new Location(12.9716, 77.5946),
                ve.getAvailableFrom()
            )
        ).toList();

        List<Trip> trips = tEntities.stream().map(te ->
            new Trip(
                te.getId(),
                tenantId,
                new Location(12.9716, 77.5946),
                new Location(12.9352, 77.6245),
                te.getDistanceKm(),
                te.getPickupWindowStart(),
                te.getDeadline()
            )
        ).toList();

        TariffSchedule schedule = new TariffSchedule(
            ZoneId.of("Asia/Kolkata"), "INR", List.of(), TariffRate.of(8.50, "INR")
        );

        List<ChargerHub> hubs = hEntities.stream().map(he ->
            new ChargerHub(
                he.getId(),
                tenantId,
                new Location(12.9915, 77.5583),
                he.getTotalChargers(),
                50.0,
                schedule
            )
        ).toList();

        return new PlanningInput("RUN-" + UUID.randomUUID(), vehicles, trips, hubs);
    }
}
