package com.moveinsync.evfleet.api.controller;

import com.moveinsync.evfleet.api.dto.OverviewResponse;
import com.moveinsync.evfleet.persistence.service.FleetPersistenceService;
import com.moveinsync.evfleet.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/overview")
@Tag(name = "Overview", description = "Operational Overview & Summary Aggregates")
public class OverviewController {

    private final FleetPersistenceService fleetService;

    public OverviewController(FleetPersistenceService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping
    @Operation(summary = "Get aggregated operational overview metrics for authenticated tenant")
    public ResponseEntity<OverviewResponse> getOverview() {
        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        OverviewResponse response = fleetService.getOverview(tenantId);
        return ResponseEntity.ok(response);
    }
}
