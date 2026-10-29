package com.moveinsync.evfleet.api.controller;

import com.moveinsync.evfleet.api.dto.VehicleRequest;
import com.moveinsync.evfleet.api.dto.VehicleResponse;
import com.moveinsync.evfleet.persistence.entity.VehicleEntity;
import com.moveinsync.evfleet.persistence.service.FleetPersistenceService;
import com.moveinsync.evfleet.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles", description = "Vehicle Registration and Fleet Management")
public class FleetController {

    private final FleetPersistenceService fleetService;

    public FleetController(FleetPersistenceService fleetService) {
        this.fleetService = fleetService;
    }

    @PostMapping
    @Operation(summary = "Register or update an EV in the fleet")
    public ResponseEntity<VehicleResponse> registerVehicle(
            @Valid @RequestBody VehicleRequest request) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        VehicleEntity entity = new VehicleEntity(
            request.id(),
            tenantId,
            request.currentSoc(),
            request.batteryCapacityKwh(),
            request.efficiencyKwhPerKm(),
            request.maxChargeRateKw(),
            request.currentLocationId(),
            request.availableFrom()
        );

        VehicleEntity saved = fleetService.registerVehicle(tenantId, entity);
        VehicleResponse response = new VehicleResponse(
            saved.getId(),
            saved.getTenantId(),
            saved.getCurrentSoc(),
            saved.getBatteryCapacityKwh(),
            saved.getEfficiencyKwhPerKm(),
            saved.getMaxChargeRateKw(),
            saved.getCurrentLocationId(),
            saved.getAvailableFrom()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all vehicles for tenant")
    public ResponseEntity<List<VehicleResponse>> listVehicles() {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        List<VehicleResponse> responses = fleetService.getVehicles(tenantId).stream()
            .map(v -> new VehicleResponse(
                v.getId(),
                v.getTenantId(),
                v.getCurrentSoc(),
                v.getBatteryCapacityKwh(),
                v.getEfficiencyKwhPerKm(),
                v.getMaxChargeRateKw(),
                v.getCurrentLocationId(),
                v.getAvailableFrom()
            )).toList();
        return ResponseEntity.ok(responses);
    }
}
