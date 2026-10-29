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
    int totalChargers,

    @Schema(description = "Power output per charger plug (kW)", example = "50.0")
    double chargerPowerKw,

    @Schema(description = "Tariff rate per kWh", example = "8.50")
    double tariffRate,

    @Schema(description = "Operational status", example = "OPERATIONAL")
    String status,

    @Schema(description = "Latitude", example = "12.9352")
    double latitude,

    @Schema(description = "Longitude", example = "77.6245")
    double longitude,

    @Schema(description = "Currently active/occupied charging sessions count", example = "2")
    int activeSessionsCount,

    @Schema(description = "Available chargers count", example = "2")
    int availableChargersCount
) {
    public ChargerHubResponse(String id, String tenantId, String name, String locationId, int totalChargers) {
        this(id, tenantId, name, locationId, totalChargers, 50.0, 8.50, "OPERATIONAL", 12.9716, 77.5946, 0, totalChargers);
    }
}
