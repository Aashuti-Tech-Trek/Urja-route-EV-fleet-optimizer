package com.moveinsync.evfleet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.evfleet.api.dto.ChargerHubRequest;
import com.moveinsync.evfleet.api.dto.TripRequest;
import com.moveinsync.evfleet.api.dto.VehicleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test verifying that the REST endpoints backing the frontend dashboard
 * (Overview, Vehicles, Trips, Hubs) maintain consistency, support RBAC, and handle
 * empty state and registered state accurately without returning synthetic demo mock data.
 *
 * <p>Note: Tenant identity is derived from the authenticated user principal
 * via {@code SecurityUtils.getAuthenticatedTenantId()}, not from X-Tenant-ID header.
 * Each @WithMockUser username acts as a distinct tenant boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Empty state: Fresh tenant returns empty lists and zeroed summary counts")
    @WithMockUser(username = "fresh_tenant_user", roles = {"OPERATOR"})
    void testEmptyStateForNewTenant() throws Exception {
        // Overview should have zeroed counts for a tenant that has never registered data
        mockMvc.perform(get("/api/v1/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCount", is(0)))
                .andExpect(jsonPath("$.tripCount", is(0)))
                .andExpect(jsonPath("$.hubCount", is(0)))
                .andExpect(jsonPath("$.totalChargers", is(0)))
                .andExpect(jsonPath("$.evAssignedTripCount", is(0)))
                .andExpect(jsonPath("$.backupTripCount", is(0)));

        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Integration: Registering vehicle, trip, and hub updates overview summary and details consistently")
    @WithMockUser(username = "dashboard_test_user", roles = {"OPERATOR"})
    void testRegisterEntitiesAndVerifyConsistency() throws Exception {
        Instant now = Instant.now();

        // 1. Register a Vehicle
        VehicleRequest vehicleReq = new VehicleRequest(
                "EV-DASH-1", 0.85, 60.0, 0.20, 50.0, "LOC-DEPOT", now
        );
        mockMvc.perform(post("/api/v1/vehicles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vehicleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("EV-DASH-1")));

        // 2. Register a Trip
        TripRequest tripReq = new TripRequest(
                "TRIP-DASH-1", "LOC-A", "LOC-B",
                now.plusSeconds(600), now.plusSeconds(1800), now.plusSeconds(3600),
                15.0, 4
        );
        mockMvc.perform(post("/api/v1/trips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tripReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("TRIP-DASH-1")));

        // 3. Register a Charger Hub
        ChargerHubRequest hubReq = new ChargerHubRequest(
                "HUB-DASH-1", "Central Charging Station", "LOC-HUB-1", 4
        );
        mockMvc.perform(post("/api/v1/hubs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hubReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("HUB-DASH-1")))
                .andExpect(jsonPath("$.totalChargers", is(4)));

        // 4. Verify Overview cards reflect the exact newly registered counts
        mockMvc.perform(get("/api/v1/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCount", is(1)))
                .andExpect(jsonPath("$.tripCount", is(1)))
                .andExpect(jsonPath("$.hubCount", is(1)))
                .andExpect(jsonPath("$.totalChargers", is(4)));

        // 5. Verify detailed lists match
        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("EV-DASH-1")));

        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("TRIP-DASH-1")));

        mockMvc.perform(get("/api/v1/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("HUB-DASH-1")));
    }
}
