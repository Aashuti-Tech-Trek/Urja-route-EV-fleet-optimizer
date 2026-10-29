package com.moveinsync.evfleet.api.controller;

import com.moveinsync.evfleet.api.dto.ChargerHubRequest;
import com.moveinsync.evfleet.api.dto.ChargerHubResponse;
import com.moveinsync.evfleet.persistence.entity.ChargerHubEntity;
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
@RequestMapping("/api/v1/hubs")
@Tag(name = "Hubs", description = "Charger Hub Registration and Network Inspection")
public class HubController {

    private final FleetPersistenceService fleetService;

    public HubController(FleetPersistenceService fleetService) {
        this.fleetService = fleetService;
    }

    @PostMapping
    @Operation(summary = "Register or update a charger hub")
    public ResponseEntity<ChargerHubResponse> registerHub(
            @Valid @RequestBody ChargerHubRequest request) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        ChargerHubEntity entity = new ChargerHubEntity(
            request.id(),
            tenantId,
            request.name(),
            request.locationId(),
            request.totalChargers()
        );

        ChargerHubEntity saved = fleetService.registerHub(tenantId, entity);
        ChargerHubResponse response = new ChargerHubResponse(
            saved.getId(),
            saved.getTenantId(),
            saved.getName(),
            saved.getLocationId(),
            saved.getTotalChargers()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all charger hubs for tenant")
    public ResponseEntity<List<ChargerHubResponse>> listHubs() {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        List<ChargerHubResponse> responses = fleetService.getHubs(tenantId).stream()
            .map(h -> new ChargerHubResponse(
                h.getId(),
                h.getTenantId(),
                h.getName(),
                h.getLocationId(),
                h.getTotalChargers()
            )).toList();
        return ResponseEntity.ok(responses);
    }
}
