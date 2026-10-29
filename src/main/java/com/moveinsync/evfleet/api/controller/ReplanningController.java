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
import com.moveinsync.evfleet.security.SecurityUtils;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody BatteryDriftRequest request) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();

        // 1. Validate vehicle ownership & existence for tenant
        List<VehicleEntity> vehicles = fleetService.getVehicles(tenantId);
        boolean vehicleExists = vehicles.stream().anyMatch(v -> v.getId().equals(request.vehicleId()));
        if (!vehicleExists) {
            fleetService.recordAuditEvent(
                tenantId, "BATTERY_DRIFT_REJECTED", "VEHICLE", request.vehicleId(),
                "Vehicle does not belong to tenant " + tenantId + " or not found"
            );
            throw new IllegalArgumentException("Vehicle " + request.vehicleId() + " does not belong to tenant " + tenantId);
        }

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

        // 2. Persist updated observed battery state to vehicle entity
        vehicles.stream()
                .filter(v -> v.getId().equals(request.vehicleId()))
                .findFirst()
                .ifPresent(v -> {
                    v.setCurrentSoc(request.observedSoc());
                    fleetService.registerVehicle(tenantId, v);
                });

        // 3. Record auditable recovery event
        fleetService.recordAuditEvent(
            tenantId, "BATTERY_DRIFT_RECOVERY", "VEHICLE", request.vehicleId(),
            String.format("Observed SoC=%.2f, outcome=%s, durationMs=%d, valid=%b",
                    request.observedSoc(), result.getOutcome().name(),
                    result.getElapsedDuration().toMillis(),
                    result.getValidationReport().map(r -> r.isValid()).orElse(false))
        );

        return ResponseEntity.ok(toApiResponse(result));
    }

    @PostMapping("/charger-failure")
    @Operation(summary = "Handle charger hub outage event under 2.0s SLA")
    public ResponseEntity<ReplanningApiResponse> handleChargerFailure(
            @Valid @RequestBody ChargerFailureRequest request) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();

        // 1. Validate hub ownership & existence for tenant
        List<ChargerHubEntity> hubs = fleetService.getHubs(tenantId);
        boolean hubExists = hubs.stream().anyMatch(h -> h.getId().equals(request.hubId()));
        if (!hubExists) {
            fleetService.recordAuditEvent(
                tenantId, "CHARGER_FAILURE_REJECTED", "HUB", request.hubId(),
                "Charger hub does not belong to tenant " + tenantId + " or not found"
            );
            throw new IllegalArgumentException("Charger hub " + request.hubId() + " does not belong to tenant " + tenantId);
        }

        // Build baseline plan before marking offline so current plan reflects the schedule before the failure
        PlanningInput inputBeforeFailure = buildDefaultInput(tenantId, false);
        FleetPlanResult currentPlan = GreedyFleetPlanner.createFastReplanner().plan(inputBeforeFailure);

        // Mark hub status as OFFLINE
        fleetService.setHubStatus(tenantId, request.hubId(), "OFFLINE");

        ChargerFailureEvent event = new ChargerFailureEvent(
            UUID.randomUUID().toString(),
            request.hubId(),
            request.outageStart(),
            request.estimatedRestoration()
        );

        ReplanningResult result = replanningService.handleChargerFailure(
            inputBeforeFailure,
            currentPlan,
            event
        );

        // Record auditable recovery event
        fleetService.recordAuditEvent(
            tenantId, "CHARGER_FAILURE_RECOVERY", "HUB", request.hubId(),
            String.format("Outage from %s to %s, outcome=%s, durationMs=%d, valid=%b",
                    request.outageStart(), request.estimatedRestoration(),
                    result.getOutcome().name(),
                    result.getElapsedDuration().toMillis(),
                    result.getValidationReport().map(r -> r.isValid()).orElse(false))
        );

        return ResponseEntity.ok(toApiResponse(result));
    }

    @PostMapping("/charger-restore/{hubId}")
    @Operation(summary = "Explicitly restore an offline charger hub back to OPERATIONAL")
    public ResponseEntity<ReplanningApiResponse> restoreCharger(@PathVariable String hubId) {
        String tenantId = SecurityUtils.getAuthenticatedTenantId();

        ChargerHubEntity hub = fleetService.getHub(tenantId, hubId)
                .orElseThrow(() -> new IllegalArgumentException("Charger hub " + hubId + " does not belong to tenant " + tenantId));

        fleetService.setHubStatus(tenantId, hubId, "OPERATIONAL");

        fleetService.recordAuditEvent(
            tenantId, "CHARGER_RESTORED", "HUB", hubId,
            "Charger hub restored to OPERATIONAL status"
        );

        PlanningInput input = buildDefaultInput(tenantId);
        FleetPlanResult restoredPlan = GreedyFleetPlanner.createFastReplanner().plan(input);

        return ResponseEntity.ok(new ReplanningApiResponse(
            "SUCCESS",
            UUID.randomUUID().toString(),
            restoredPlan.getTotalChargingCost().doubleValue(),
            0.0,
            List.of("restoredHub=" + hubId),
            List.of("preservedCount=" + restoredPlan.getTripAssignments().size()),
            0L,
            true
        ));
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
        return buildDefaultInput(tenantId, true);
    }

    private PlanningInput buildDefaultInput(String tenantId, boolean filterOffline) {
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

        List<ChargerHub> hubs = hEntities.stream()
            .filter(he -> !filterOffline || !"OFFLINE".equalsIgnoreCase(he.getStatus()))
            .map(he -> {
                TariffSchedule schedule = new TariffSchedule(
                    ZoneId.of("Asia/Kolkata"), "INR", List.of(), TariffRate.of(he.getTariffRate(), "INR")
                );
                return new ChargerHub(
                    he.getId(),
                    tenantId,
                    new Location(he.getLatitude(), he.getLongitude()),
                    he.getTotalChargers(),
                    he.getChargerPowerKw(),
                    schedule
                );
            }).toList();

        return new PlanningInput("RUN-" + UUID.randomUUID(), vehicles, trips, hubs);
    }
}
