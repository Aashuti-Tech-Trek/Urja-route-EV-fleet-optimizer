package com.moveinsync.evfleet.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.evfleet.api.dto.AcceptPlanRequest;
import com.moveinsync.evfleet.api.dto.GeneratePlanRequest;
import com.moveinsync.evfleet.api.dto.PlanProposalResponse;
import com.moveinsync.evfleet.api.dto.PlanVersionResponse;
import com.moveinsync.evfleet.persistence.entity.IdempotencyRecordEntity;
import com.moveinsync.evfleet.persistence.service.FleetPersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Planning", description = "Plan Generation, Proposals, and Version Acceptance")
public class PlanningController {

    private final FleetPersistenceService fleetService;
    private final ObjectMapper objectMapper;

    public PlanningController(FleetPersistenceService fleetService) {
        this.fleetService = fleetService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostMapping("/propose")
    @Operation(summary = "Generate and independently validate a candidate fleet plan proposal")
    public ResponseEntity<PlanProposalResponse> proposePlan(
            @RequestHeader(name = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody GeneratePlanRequest request) {

        PlanProposalResponse response = fleetService.generatePlanProposal(
            tenantId,
            request.planningHorizonStart(),
            request.planningHorizonEnd(),
            request.vehicleIds(),
            request.tripIds()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{proposalId}/accept")
    @Operation(summary = "Atomically accept a plan proposal with optimistic locking and idempotency")
    public ResponseEntity<?> acceptPlan(
            @RequestHeader(name = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String proposalId,
            @Valid @RequestBody AcceptPlanRequest request) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecordEntity> existing = fleetService.getIdempotencyRecord(idempotencyKey, tenantId);
            if (existing.isPresent()) {
                try {
                    PlanVersionResponse cached = objectMapper.readValue(existing.get().getResponsePayload(), PlanVersionResponse.class);
                    return ResponseEntity.status(existing.get().getHttpStatus()).body(cached);
                } catch (JsonProcessingException ignored) {}
            }
        }

        PlanVersionResponse response = fleetService.acceptPlan(tenantId, proposalId, request.expectedCurrentVersion());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String json = objectMapper.writeValueAsString(response);
                fleetService.saveIdempotencyRecord(idempotencyKey, tenantId, json, HttpStatus.OK.value());
            } catch (JsonProcessingException ignored) {}
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/versions")
    @Operation(summary = "List plan versions for tenant")
    public ResponseEntity<List<PlanVersionResponse>> listVersions(
            @RequestHeader(name = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        return ResponseEntity.ok(fleetService.getPlanVersions(tenantId));
    }
}
