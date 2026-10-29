package com.moveinsync.evfleet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OverviewResponse(
    @Schema(description = "Total number of registered vehicles", example = "30")
    int vehicleCount,

    @Schema(description = "Total number of trips in shift", example = "1248")
    int tripCount,

    @Schema(description = "Total number of charger hubs", example = "5")
    int hubCount,

    @Schema(description = "Total number of physical charger plugs", example = "20")
    int totalChargers,

    @Schema(description = "Current active plan version number", example = "1")
    long currentVersion,

    @Schema(description = "Service coverage rate of current plan (0.0 to 1.0)", example = "0.95")
    double serviceRate,

    @Schema(description = "Total charging cost of latest plan", example = "8460.0")
    double latestPlanCost,

    @Schema(description = "Number of trips assigned to EVs", example = "1180")
    int evAssignedTripCount,

    @Schema(description = "Number of trips requiring backup cabs", example = "68")
    int backupTripCount
) {}
