package com.moveinsync.evfleet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.evfleet.api.dto.*;
import com.moveinsync.evfleet.persistence.entity.ChargerHubEntity;
import com.moveinsync.evfleet.persistence.service.FleetPersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityAndReliabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FleetPersistenceService fleetService;

    // Helper headers
    private static final String ADMIN_KEY = "key-admin-tenant-sec";
    private static final String OPERATOR_KEY = "key-operator-tenant-sec";
    private static final String VIEWER_KEY = "key-viewer-tenant-sec";

    @Test
    @DisplayName("Authentication: Requests without credentials or with invalid keys return 401 Unauthorized")
    void testMissingOrInvalidCredentialsReturns401() throws Exception {
        // Missing key
        mockMvc.perform(get("/api/v1/vehicles"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Unauthorized"));

        // Invalid key
        mockMvc.perform(get("/api/v1/vehicles")
                .header("X-API-KEY", "invalid-secret-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("RBAC: VIEWER role cannot register vehicles or accept plans (returns 403 Forbidden)")
    void testViewerRoleMutationsReturn403() throws Exception {
        Instant now = Instant.now();
        VehicleRequest vReq = new VehicleRequest(
            "EV-VIEWER-DENIED", 0.90, 60.0, 0.20, 50.0, "BASE", now
        );

        // Viewer trying to POST vehicle
        mockMvc.perform(post("/api/v1/vehicles")
                .header("X-API-KEY", VIEWER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Forbidden"));

        // Viewer trying to accept plan
        AcceptPlanRequest acceptReq = new AcceptPlanRequest(0L);
        mockMvc.perform(post("/api/v1/plans/sample-proposal/accept")
                .header("X-API-KEY", VIEWER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("RBAC: OPERATOR and ADMIN can read and mutate authorized fleet resources")
    void testOperatorAndAdminAllowedOperations() throws Exception {
        Instant now = Instant.now();
        VehicleRequest vReq = new VehicleRequest(
            "EV-OP-ALLOWED", 0.90, 60.0, 0.20, 50.0, "BASE", now
        );

        // Operator registering vehicle -> 201
        mockMvc.perform(post("/api/v1/vehicles")
                .header("X-API-KEY", OPERATOR_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("EV-OP-ALLOWED"));

        // Viewer reading vehicles -> 200
        mockMvc.perform(get("/api/v1/vehicles")
                .header("X-API-KEY", VIEWER_KEY))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant Isolation: Operator of Tenant A cannot query or mutate Tenant B resources")
    void testStrictTenantIsolation() throws Exception {
        String tenantA = "tenant-sec-a-" + UUID.randomUUID();
        String tenantB = "tenant-sec-b-" + UUID.randomUUID();

        String keyA = "key-operator-" + tenantA;
        String keyB = "key-operator-" + tenantB;

        Instant now = Instant.now();

        // 1. Tenant A registers a vehicle
        VehicleRequest vA = new VehicleRequest(
            "EV-TENANT-A", 0.90, 60.0, 0.20, 50.0, "BASE", now
        );
        mockMvc.perform(post("/api/v1/vehicles")
                .header("X-API-KEY", keyA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vA)))
            .andExpect(status().isCreated());

        // 2. Tenant B lists vehicles -> must NOT see Tenant A's vehicle
        mockMvc.perform(get("/api/v1/vehicles")
                .header("X-API-KEY", keyB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        // 3. Tenant B attempts planning referencing Tenant A's vehicle -> 400 Bad Request
        GeneratePlanRequest crossPlanReq = new GeneratePlanRequest(
            now, now.plus(Duration.ofHours(12)),
            List.of("EV-TENANT-A"), List.of()
        );
        mockMvc.perform(post("/api/v1/plans/propose")
                .header("X-API-KEY", keyB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(crossPlanReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Idempotency: Reusing Idempotency-Key with conflicting payload returns 409 Conflict")
    void testIdempotencyKeyWithMismatchedPayloadReturns409() throws Exception {
        String tenant = "tenant-idem-sec-" + UUID.randomUUID();
        String key = "key-operator-" + tenant;
        fleetService.registerHub(tenant, new ChargerHubEntity("hub-1", tenant, "Central Hub", "HUB_LOC", 4));
        Instant now = Instant.now();

        fleetService.registerVehicle(tenant, new com.moveinsync.evfleet.persistence.entity.VehicleEntity(
            "EV-SEC-IDEM", tenant, 0.90, 60.0, 0.20, 50.0, "BASE", now
        ));
        fleetService.registerTrip(tenant, new com.moveinsync.evfleet.persistence.entity.TripEntity(
            "TRIP-SEC-IDEM", tenant, "BASE", "AIRPORT", now.plus(Duration.ofMinutes(10)),
            now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofMinutes(60)), 10.0, 1, "PENDING"
        ));

        PlanProposalResponse proposal = fleetService.generatePlanProposal(
            tenant, now, now.plus(Duration.ofHours(12)), List.of("EV-SEC-IDEM"), List.of("TRIP-SEC-IDEM")
        );

        String idempotencyKey = "IDEM-TEST-" + UUID.randomUUID();
        AcceptPlanRequest acceptReq1 = new AcceptPlanRequest(0L);

        // First acceptance -> 200
        mockMvc.perform(post("/api/v1/plans/" + proposal.proposalId() + "/accept")
                .header("X-API-KEY", key)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq1)))
            .andExpect(status().isOk());

        // Reusing same Idempotency-Key with different payload (e.g. expectedVersion 1 instead of 0) -> 409 Conflict
        AcceptPlanRequest acceptReq2 = new AcceptPlanRequest(1L);
        mockMvc.perform(post("/api/v1/plans/" + proposal.proposalId() + "/accept")
                .header("X-API-KEY", key)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict / State Conflict"));
    }

    @Test
    @DisplayName("Request Validation: Malformed / NaN / negative fields return 400 Bad Request")
    void testRequestValidationBoundaries() throws Exception {
        Instant now = Instant.now();

        // 1. Invalid SoC (> 1.0)
        VehicleRequest badSoc = new VehicleRequest(
            "EV-BAD-SOC", 1.5, 60.0, 0.20, 50.0, "BASE", now
        );
        mockMvc.perform(post("/api/v1/vehicles")
                .header("X-API-KEY", OPERATOR_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badSoc)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation Error"));

        // 2. Inverted trip window via JSON payload
        String badWindowJson = String.format("""
            {
              "id": "TRIP-BAD-WINDOW",
              "pickupLocationId": "BASE",
              "dropoffLocationId": "AIRPORT",
              "pickupWindowStart": "%s",
              "pickupWindowEnd": "%s",
              "deadline": "%s",
              "distanceKm": 10.0,
              "passengerCount": 1
            }
            """,
            now.plus(Duration.ofMinutes(30)),
            now.plus(Duration.ofMinutes(10)),
            now.plus(Duration.ofMinutes(60))
        );

        mockMvc.perform(post("/api/v1/trips")
                .header("X-API-KEY", OPERATOR_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(badWindowJson))
            .andExpect(status().isBadRequest());
    }
}
