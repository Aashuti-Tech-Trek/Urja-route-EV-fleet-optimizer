package com.moveinsync.evfleet.api.controller;

import com.moveinsync.evfleet.api.dto.TripRequest;
import com.moveinsync.evfleet.api.dto.TripResponse;
import com.moveinsync.evfleet.persistence.entity.TripEntity;
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
@RequestMapping("/api/v1/trips")
@Tag(name = "Trips", description = "Trip Demand Ingestion and Status")
public class TripController {

    private final FleetPersistenceService fleetService;

    public TripController(FleetPersistenceService fleetService) {
        this.fleetService = fleetService;
    }

    @PostMapping
    @Operation(summary = "Ingest a trip demand request")
    public ResponseEntity<TripResponse> registerTrip(
            @Valid @RequestBody TripRequest request) {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        TripEntity entity = new TripEntity(
            request.id(),
            tenantId,
            request.pickupLocationId(),
            request.dropoffLocationId(),
            request.pickupWindowStart(),
            request.pickupWindowEnd(),
            request.deadline(),
            request.distanceKm(),
            request.passengerCount(),
            "PENDING"
        );

        TripEntity saved = fleetService.registerTrip(tenantId, entity);
        TripResponse response = new TripResponse(
            saved.getId(),
            saved.getTenantId(),
            saved.getPickupLocationId(),
            saved.getDropoffLocationId(),
            saved.getPickupWindowStart(),
            saved.getPickupWindowEnd(),
            saved.getDeadline(),
            saved.getDistanceKm(),
            saved.getPassengerCount(),
            saved.getStatus()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all trips for tenant")
    public ResponseEntity<List<TripResponse>> listTrips() {

        String tenantId = SecurityUtils.getAuthenticatedTenantId();
        List<TripResponse> responses = fleetService.getTrips(tenantId).stream()
            .map(t -> new TripResponse(
                t.getId(),
                t.getTenantId(),
                t.getPickupLocationId(),
                t.getDropoffLocationId(),
                t.getPickupWindowStart(),
                t.getPickupWindowEnd(),
                t.getDeadline(),
                t.getDistanceKm(),
                t.getPassengerCount(),
                t.getStatus()
            )).toList();
        return ResponseEntity.ok(responses);
    }
}
