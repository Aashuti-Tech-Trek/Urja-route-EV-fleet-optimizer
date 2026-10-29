package com.moveinsync.evfleet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChargerHubResponse(
    @Schema(description = "Hub ID", example = "HUB-BELLANDUR")
    String id,

    @Schema(description = "Tenant ID", example = "tenant-1")
    String tenantId,

    @Schema(description = "Hub Name", example = "Bellandur Tech Park Hub")
    String name,

    @Schema(description = "Location ID", example = "BELLANDUR")
    String locationId,

    @Schema(description = "Total Chargers Count", example = "4")
    int totalChargers
) {}
