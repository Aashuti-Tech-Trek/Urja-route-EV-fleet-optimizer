package com.moveinsync.evfleet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.evfleet.api.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 10 & 11 Integration Suite:
 * - Phase 10: Complete Hub CRUD, pagination, detail retrieval, config updates,
 *             utilization inspection, status setting, capacity enforcement, tenant isolation.
 * - Phase 11: Real battery-drift recovery with state persistence & audit,
 *             charger-failure recovery with offline marking & audit,
 *             explicit restoration, cross-tenant event rejection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HubAndRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("Phase 10: Hub Management & Tenant Operations")
    class Phase10HubManagementTests {

        @Test
        @DisplayName("10.1 & 10.2: Hub CRUD, pagination, detail retrieval, and tenant isolation")
        @WithMockUser(username = "tenant_alpha", roles = {"OPERATOR"})
        void testHubCrudAndPagination() throws Exception {
            // 1. Create Hub
            ChargerHubRequest hubReq = new ChargerHubRequest(
                "HUB-ALPHA-1", "Alpha Main Charging Depot", "LOC-ALPHA-HUB", 6,
                60.0, 9.50, "OPERATIONAL", 12.9352, 77.6245
            );

            mockMvc.perform(post("/api/v1/hubs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(hubReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is("HUB-ALPHA-1")))
                    .andExpect(jsonPath("$.name", is("Alpha Main Charging Depot")))
                    .andExpect(jsonPath("$.totalChargers", is(6)))
                    .andExpect(jsonPath("$.chargerPowerKw", is(60.0)))
                    .andExpect(jsonPath("$.tariffRate", is(9.50)))
                    .andExpect(jsonPath("$.status", is("OPERATIONAL")));

            // 2. Retrieve Hub Details by ID
            mockMvc.perform(get("/api/v1/hubs/HUB-ALPHA-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is("HUB-ALPHA-1")))
                    .andExpect(jsonPath("$.locationId", is("LOC-ALPHA-HUB")));

            // 3. Paginated listing
            mockMvc.perform(get("/api/v1/hubs?page=0&size=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements", is(1)));

            // 4. Update Hub configuration
            ChargerHubRequest updateReq = new ChargerHubRequest(
                "HUB-ALPHA-1", "Alpha Supercharger Station", "LOC-ALPHA-EXPANDED", 8,
                120.0, 11.0, "OPERATIONAL", 12.9352, 77.6245
            );

            mockMvc.perform(put("/api/v1/hubs/HUB-ALPHA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Alpha Supercharger Station")))
                    .andExpect(jsonPath("$.totalChargers", is(8)))
                    .andExpect(jsonPath("$.chargerPowerKw", is(120.0)));

            // 5. Utilization inspection
            mockMvc.perform(get("/api/v1/hubs/HUB-ALPHA-1/utilization"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hubId", is("HUB-ALPHA-1")))
                    .andExpect(jsonPath("$.totalChargers", is(8)))
                    .andExpect(jsonPath("$.availableChargers", is(8)))
                    .andExpect(jsonPath("$.occupiedChargers", is(0)));

            // 6. Set hub status to OFFLINE
            mockMvc.perform(post("/api/v1/hubs/HUB-ALPHA-1/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "OFFLINE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("OFFLINE")));

            // 7. Verify utilization reflects OFFLINE state
            mockMvc.perform(get("/api/v1/hubs/HUB-ALPHA-1/utilization"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offlineChargers", is(8)))
                    .andExpect(jsonPath("$.availableChargers", is(0)));
        }

        @Test
        @DisplayName("10.2: Tenant isolation: tenant_beta cannot view or mutate tenant_alpha's hub")
        @WithMockUser(username = "tenant_beta", roles = {"OPERATOR"})
        void testCrossTenantHubIsolation() throws Exception {
            // tenant_beta attempts to read tenant_alpha's hub -> 400 Bad Request (Safe error without data disclosure)
            mockMvc.perform(get("/api/v1/hubs/HUB-ALPHA-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Bad Request")));

            // tenant_beta attempts to update tenant_alpha's hub -> 400 Bad Request
            ChargerHubRequest hackReq = new ChargerHubRequest(
                "HUB-ALPHA-1", "Hacked Hub", "LOC-HACK", 10,
                50.0, 5.0, "OPERATIONAL", 0.0, 0.0
            );
            mockMvc.perform(put("/api/v1/hubs/HUB-ALPHA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(hackReq)))
                    .andExpect(status().isBadRequest());

            // tenant_beta's hub list is empty
            mockMvc.perform(get("/api/v1/hubs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("10.4: Overview reconciles available, occupied, and offline chargers")
        @WithMockUser(username = "tenant_overview_test", roles = {"OPERATOR"})
        void testOverviewChargerReconciliation() throws Exception {
            // Register an operational hub with 4 chargers
            ChargerHubRequest hub1 = new ChargerHubRequest("HUB-OV-1", "Hub One", "LOC-1", 4);
            mockMvc.perform(post("/api/v1/hubs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(hub1)))
                    .andExpect(status().isCreated());

            // Register an offline hub with 2 chargers
            ChargerHubRequest hub2 = new ChargerHubRequest(
                "HUB-OV-2", "Hub Two Offline", "LOC-2", 2,
                50.0, 8.50, "OFFLINE", 12.9, 77.6
            );
            mockMvc.perform(post("/api/v1/hubs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(hub2)))
                    .andExpect(status().isCreated());

            // Check overview reconciles total=6, available=4, offline=2
            mockMvc.perform(get("/api/v1/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hubCount", is(2)))
                    .andExpect(jsonPath("$.totalChargers", is(6)))
                    .andExpect(jsonPath("$.availableChargers", is(4)))
                    .andExpect(jsonPath("$.offlineChargers", is(2)));
        }
    }

    @Nested
    @DisplayName("Phase 11: Live Recovery and Operator Workflows")
    class Phase11RecoveryTests {

        @Test
        @DisplayName("11.1 & 11.2: Battery drift workflow with state persistence, SLA, and audit trail")
        @WithMockUser(username = "tenant_recovery_ops", roles = {"OPERATOR"})
        void testBatteryDriftWorkflow() throws Exception {
            Instant now = Instant.now();

            // 1. Setup vehicle
            VehicleRequest vReq = new VehicleRequest(
                "EV-RECOV-1", 0.90, 60.0, 0.20, 50.0, "LOC-BASE", now
            );
            mockMvc.perform(post("/api/v1/vehicles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(vReq)))
                    .andExpect(status().isCreated());

            // 2. Setup trip
            TripRequest tReq = new TripRequest(
                "TRIP-RECOV-1", "LOC-BASE", "LOC-AIRPORT",
                now.plusSeconds(300), now.plusSeconds(900), now.plusSeconds(1800),
                15.0, 2
            );
            mockMvc.perform(post("/api/v1/trips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(tReq)))
                    .andExpect(status().isCreated());

            // 3. Trigger battery drift event
            BatteryDriftRequest driftReq = new BatteryDriftRequest(
                "EV-RECOV-1", 0.75, now.plusSeconds(60)
            );
            mockMvc.perform(post("/api/v1/replanning/battery-drift")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(driftReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome", notNullValue()))
                    .andExpect(jsonPath("$.replanDurationMs", lessThan(2000)));

            // 4. Verify vehicle entity in DB was updated to observed SoC (0.75)
            mockMvc.perform(get("/api/v1/vehicles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].currentSoc", is(0.75)));

            // 5. Verify audit event was logged
            mockMvc.perform(get("/api/v1/hubs/recovery-events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$[0].action", containsString("DRIFT")));
        }

        @Test
        @DisplayName("11.2: Charger failure marks hub offline, audits outage, and allows restoration")
        @WithMockUser(username = "tenant_charger_fail_ops", roles = {"OPERATOR"})
        void testChargerFailureAndRestoration() throws Exception {
            Instant now = Instant.now();

            // 1. Setup hub
            ChargerHubRequest hubReq = new ChargerHubRequest(
                "HUB-FAIL-1", "East Charger Hub", "LOC-EAST", 4
            );
            mockMvc.perform(post("/api/v1/hubs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(hubReq)))
                    .andExpect(status().isCreated());

            // 2. Trigger charger failure event
            ChargerFailureRequest failReq = new ChargerFailureRequest(
                "HUB-FAIL-1", now, now.plusSeconds(7200)
            );
            mockMvc.perform(post("/api/v1/replanning/charger-failure")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(failReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome", notNullValue()))
                    .andExpect(jsonPath("$.replanDurationMs", lessThan(2000)));

            // 3. Verify hub is now OFFLINE in hub details and overview
            mockMvc.perform(get("/api/v1/hubs/HUB-FAIL-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("OFFLINE")));

            mockMvc.perform(get("/api/v1/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offlineChargers", is(4)))
                    .andExpect(jsonPath("$.availableChargers", is(0)));

            // 4. Restore the charger
            mockMvc.perform(post("/api/v1/replanning/charger-restore/HUB-FAIL-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome", is("SUCCESS")));

            // 5. Verify hub is back to OPERATIONAL
            mockMvc.perform(get("/api/v1/hubs/HUB-FAIL-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("OPERATIONAL")));

            mockMvc.perform(get("/api/v1/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offlineChargers", is(0)))
                    .andExpect(jsonPath("$.availableChargers", is(4)));
        }

        @Test
        @DisplayName("11.6: Cross-tenant recovery event rejection")
        @WithMockUser(username = "tenant_intruder", roles = {"OPERATOR"})
        void testCrossTenantRecoveryRejection() throws Exception {
            Instant now = Instant.now();

            // Intruder attempts battery drift on EV belonging to another tenant -> 400 Bad Request
            BatteryDriftRequest intruderDrift = new BatteryDriftRequest(
                "EV-RECOV-1", 0.50, now
            );
            mockMvc.perform(post("/api/v1/replanning/battery-drift")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(intruderDrift)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Bad Request")));

            // Intruder attempts charger failure on Hub belonging to another tenant -> 400 Bad Request
            ChargerFailureRequest intruderFail = new ChargerFailureRequest(
                "HUB-FAIL-1", now, now.plusSeconds(3600)
            );
            mockMvc.perform(post("/api/v1/replanning/charger-failure")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(intruderFail)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Bad Request")));
        }
    }
}
