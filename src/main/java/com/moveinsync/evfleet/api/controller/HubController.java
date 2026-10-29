package com.moveinsync.evfleet.api.controller;

import com.moveinsync.evfleet.api.dto.ChargerHubRequest;
import com.moveinsync.evfleet.api.dto.ChargerHubResponse;
import com.moveinsync.evfleet.api.dto.HubUtilizationResponse;
import com.moveinsync.evfleet.persistence.entity.AuditRecordEntity;
import com.moveinsync.evfleet.persistence.entity.ChargerHubEntity;
import com.moveinsync.evfleet.persistence.service.FleetPersistenceService;
import com.moveinsync.evfleet.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hubs")
@Tag(name = "Hubs", description = "Charger Hub Registration, Management, and Network Inspection")
public class HubController {

    private final FleetPersistenceService fleetService;

    public HubController(FleetPersistenceService fleetService) {
        this.fleetService = fleetService;
    }

    @PostMapping
    @Operation(summary = "Register or create a charger hub")
    public ResponseEntity<ChargerHubResponse> registerHub(
            @Valid @RequestBody ChargerHubRequest request) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        ChargerHubEntity entity = new ChargerHubEntity(
            request.id(),
            tenantId,
            request.name(),
            request.locationId(),
            request.totalChargers(),
            request.chargerPowerKw() != null ? request.chargerPowerKw() : 50.0,
            request.tariffRate() != null ? request.tariffRate() : 8.50,
            request.status() != null ? request.status() : "OPERATIONAL",
            request.latitude() != null ? request.latitude() : 12.9716,
            request.longitude() != null ? request.longitude() : 77.5946
        );

        ChargerHubEntity saved = fleetService.registerHub(tenantId, entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping
    @Operation(summary = "List all charger hubs for tenant (optional pagination)")
    public ResponseEntity<?> listHubs(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();

        if (page != null && size != null) {
            Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
            Page<ChargerHubResponse> responsePage = fleetService.getHubs(tenantId, pageable)
                    .map(this::toResponse);
            return ResponseEntity.ok(new com.moveinsync.evfleet.api.dto.PageResponse<>(
                responsePage.getContent(),
                responsePage.getNumber(),
                responsePage.getSize(),
                responsePage.getTotalElements(),
                responsePage.getTotalPages()
            ));
        }

        List<ChargerHubResponse> responses = fleetService.getHubs(tenantId).stream()
            .map(this::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get charger hub details by ID")
    public ResponseEntity<ChargerHubResponse> getHubDetails(@PathVariable String id) {
        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        ChargerHubEntity hub = fleetService.getHub(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Hub not found or access denied: " + id));
        return ResponseEntity.ok(toResponse(hub));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update hub configuration")
    public ResponseEntity<ChargerHubResponse> updateHub(
            @PathVariable String id,
            @Valid @RequestBody ChargerHubRequest request) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        ChargerHubEntity entity = new ChargerHubEntity(
            id,
            tenantId,
            request.name(),
            request.locationId(),
            request.totalChargers(),
            request.chargerPowerKw() != null ? request.chargerPowerKw() : 50.0,
            request.tariffRate() != null ? request.tariffRate() : 8.50,
            request.status() != null ? request.status() : "OPERATIONAL",
            request.latitude() != null ? request.latitude() : 12.9716,
            request.longitude() != null ? request.longitude() : 77.5946
        );

        ChargerHubEntity updated = fleetService.updateHub(tenantId, id, entity);
        return ResponseEntity.ok(toResponse(updated));
    }

    @GetMapping("/{id}/utilization")
    @Operation(summary = "Inspect charger availability, utilization and active sessions")
    public ResponseEntity<HubUtilizationResponse> getHubUtilization(@PathVariable String id) {
        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        HubUtilizationResponse utilization = fleetService.getHubUtilization(tenantId, id);
        return ResponseEntity.ok(utilization);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Set operational status of a charger hub (OPERATIONAL, OFFLINE, DEGRADED)")
    public ResponseEntity<ChargerHubResponse> setHubStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        String status = body.getOrDefault("status", "OPERATIONAL").toUpperCase();
        if (!List.of("OPERATIONAL", "OFFLINE", "DEGRADED").contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Allowed: OPERATIONAL, OFFLINE, DEGRADED");
        }

        ChargerHubEntity updated = fleetService.setHubStatus(tenantId, id, status);
        return ResponseEntity.ok(toResponse(updated));
    }

    @GetMapping("/recovery-events")
    @Operation(summary = "Retrieve recent recovery and disruption events for tenant")
    public ResponseEntity<List<AuditRecordEntity>> getRecoveryEvents() {
        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        return ResponseEntity.ok(fleetService.getRecoveryEvents(tenantId));
    }

    private ChargerHubResponse toResponse(ChargerHubEntity entity) {
        int occupied = 0;
        int available = "OFFLINE".equalsIgnoreCase(entity.getStatus()) ? 0 : entity.getTotalChargers();
        return new ChargerHubResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getName(),
            entity.getLocationId(),
            entity.getTotalChargers(),
            entity.getChargerPowerKw(),
            entity.getTariffRate(),
            entity.getStatus(),
            entity.getLatitude(),
            entity.getLongitude(),
            occupied,
            available
        );
    }
}
