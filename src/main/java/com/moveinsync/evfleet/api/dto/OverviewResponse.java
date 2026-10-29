package com.moveinsync.evfleet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record OverviewResponse(
    @Schema(description = "Total number of registered vehicles", example = "30")
    int vehicleCount,

    @Schema(description = "Number of currently available vehicles", example = "28")
    int availableVehicles,

    @Schema(description = "Total number of trips in shift", example = "1248")
    int tripCount,

    @Schema(description = "Total number of pending trips", example = "12")
    int pendingTrips,

    @Schema(description = "Total number of charger hubs", example = "5")
    int hubCount,

    @Schema(description = "Total number of physical charger plugs", example = "20")
    int totalChargers,

    @Schema(description = "Available charger plugs", example = "16")
    int availableChargers,

    @Schema(description = "Occupied charger plugs", example = "3")
    int occupiedChargers,

    @Schema(description = "Offline charger plugs", example = "1")
    int offlineChargers,

    @Schema(description = "Current active plan version number", example = "1")
    long currentVersion,

    @Schema(description = "Service coverage rate of current plan (0.0 to 1.0)", example = "0.95")
    double serviceRate,

    @Schema(description = "Total charging cost of latest plan", example = "8460.0")
    double latestPlanCost,

    @Schema(description = "Number of trips assigned to EVs", example = "1180")
    int evAssignedTripCount,

    @Schema(description = "Number of trips requiring backup cabs", example = "68")
    int backupTripCount,

    @Schema(description = "Charger network utilization rate (0.0 to 1.0)", example = "0.15")
    double chargerUtilizationRate,

    @Schema(description = "Recent recovery audit event descriptions")
    List<String> recentRecoveryEvents
) {
    // Backward compatible constructor for tests
    public OverviewResponse(int vehicleCount, int tripCount, int hubCount, int totalChargers,
                            long currentVersion, double serviceRate, double latestPlanCost,
                            int evAssignedTripCount, int backupTripCount) {
        this(vehicleCount, vehicleCount, tripCount, Math.max(0, tripCount - evAssignedTripCount - backupTripCount),
             hubCount, totalChargers, totalChargers, 0, 0,
             currentVersion, serviceRate, latestPlanCost, evAssignedTripCount, backupTripCount, 0.0, List.of());
    }
}
