package com.moveinsync.evfleet.api;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PlanningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FleetPersistenceService fleetService;

    @Test
    @DisplayName("Complete end-to-end flow: Vehicle & Trip Registration, Propose Plan, and Atomic Accept")
    void testEndToEndPlanningFlow() throws Exception {
        String tenant = "tenant-e2e-" + UUID.randomUUID();
        fleetService.registerHub(tenant, new ChargerHubEntity("hub-1", tenant, "Central Hub", "HUB_LOC", 4));
        Instant now = Instant.now();

        // 1. Register vehicle
        VehicleRequest vReq = new VehicleRequest(
            "EV-IT-1", 0.90, 60.0, 0.20, 50.0, "BASE", now
        );
        mockMvc.perform(post("/api/v1/vehicles")
                .header("X-Tenant-ID", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("EV-IT-1"));

        // 2. Register trip
        TripRequest tReq = new TripRequest(
            "TRIP-IT-1", "BASE", "AIRPORT",
            now.plus(Duration.ofMinutes(10)),
            now.plus(Duration.ofMinutes(30)),
            now.plus(Duration.ofMinutes(60)),
            15.0, 1
        );
        mockMvc.perform(post("/api/v1/trips")
                .header("X-Tenant-ID", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("TRIP-IT-1"));

        // 3. Propose Plan
        GeneratePlanRequest planReq = new GeneratePlanRequest(
            now, now.plus(Duration.ofHours(12)),
            List.of("EV-IT-1"), List.of("TRIP-IT-1")
        );
        String proposeContent = mockMvc.perform(post("/api/v1/plans/propose")
                .header("X-Tenant-ID", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(planReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.proposalId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        PlanProposalResponse proposal = objectMapper.readValue(proposeContent, PlanProposalResponse.class);

        // 4. Accept Plan Proposal
        AcceptPlanRequest acceptReq = new AcceptPlanRequest(0L); // expected version 0
        mockMvc.perform(post("/api/v1/plans/" + proposal.proposalId() + "/accept")
                .header("X-Tenant-ID", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versionNumber").value(1L))
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // 5. Verify plan versions list
        mockMvc.perform(get("/api/v1/plans/versions")
                .header("X-Tenant-ID", tenant))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].versionNumber").value(1L));
    }

    @Test
    @DisplayName("Optimistic locking conflict: accepting when expectedVersion does not match returns 409")
    void testVersionConflictReturns409() throws Exception {
        String tenant = "tenant-conflict-" + UUID.randomUUID();
        fleetService.registerHub(tenant, new ChargerHubEntity("hub-1", tenant, "Central Hub", "HUB_LOC", 4));
        Instant now = Instant.now();

        fleetService.registerVehicle(tenant, new com.moveinsync.evfleet.persistence.entity.VehicleEntity(
            "EV-CONFLICT-1", tenant, 0.90, 60.0, 0.20, 50.0, "BASE", now
        ));
        fleetService.registerTrip(tenant, new com.moveinsync.evfleet.persistence.entity.TripEntity(
            "TRIP-C-1", tenant, "BASE", "AIRPORT", now.plus(Duration.ofMinutes(10)),
            now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofMinutes(60)), 10.0, 1, "PENDING"
        ));

        PlanProposalResponse proposal = fleetService.generatePlanProposal(
            tenant, now, now.plus(Duration.ofHours(12)), List.of("EV-CONFLICT-1"), List.of("TRIP-C-1")
        );

        // Mismatched expected version (e.g., 99 instead of current 0)
        AcceptPlanRequest badAcceptReq = new AcceptPlanRequest(99L);
        mockMvc.perform(post("/api/v1/plans/" + proposal.proposalId() + "/accept")
                .header("X-Tenant-ID", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badAcceptReq)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict / State Conflict"));
    }

    @Test
    @DisplayName("Idempotent acceptance: re-submitting with same Idempotency-Key returns same payload")
    void testIdempotentAcceptance() throws Exception {
        String tenant = "tenant-idem-" + UUID.randomUUID();
        fleetService.registerHub(tenant, new ChargerHubEntity("hub-1", tenant, "Central Hub", "HUB_LOC", 4));
        Instant now = Instant.now();

        fleetService.registerVehicle(tenant, new com.moveinsync.evfleet.persistence.entity.VehicleEntity(
            "EV-IDEM-1", tenant, 0.90, 60.0, 0.20, 50.0, "BASE", now
        ));
        fleetService.registerTrip(tenant, new com.moveinsync.evfleet.persistence.entity.TripEntity(
            "TRIP-IDEM-1", tenant, "BASE", "AIRPORT", now.plus(Duration.ofMinutes(10)),
            now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofMinutes(60)), 10.0, 1, "PENDING"
        ));

        PlanProposalResponse proposal = fleetService.generatePlanProposal(
            tenant, now, now.plus(Duration.ofHours(12)), List.of("EV-IDEM-1"), List.of("TRIP-IDEM-1")
        );

        AcceptPlanRequest acceptReq = new AcceptPlanRequest(0L);
        String idempotencyKey = "IDEM-KEY-" + UUID.randomUUID();

        // First call
        String response1 = mockMvc.perform(post("/api/v1/plans/" + proposal.proposalId() + "/accept")
                .header("X-Tenant-ID", tenant)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Second duplicate call with same idempotency key
        String response2 = mockMvc.perform(post("/api/v1/plans/" + proposal.proposalId() + "/accept")
                .header("X-Tenant-ID", tenant)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assert response1.equals(response2);
    }
}
