package com.moveinsync.evfleet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HubUtilizationResponse(
    @Schema(description = "Hub ID") String hubId,
    @Schema(description = "Total physical chargers") int totalChargers,
    @Schema(description = "Chargers currently available") int availableChargers,
    @Schema(description = "Chargers currently occupied") int occupiedChargers,
    @Schema(description = "Chargers offline") int offlineChargers,
    @Schema(description = "Utilization rate (0.0 to 1.0)") double utilizationRate,
    @Schema(description = "Scheduled charging sessions for this hub") List<PlanProposalResponse.ChargingSessionDto> scheduledSessions
) {}
