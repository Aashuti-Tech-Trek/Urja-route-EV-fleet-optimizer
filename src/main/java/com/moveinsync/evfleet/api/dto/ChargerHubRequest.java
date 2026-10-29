package com.moveinsync.evfleet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChargerHubRequest(
    @NotBlank(message = "Hub ID must not be blank")
    @Schema(description = "Unique Identifier for Charger Hub", example = "HUB-BELLANDUR")
    String id,

    @NotBlank(message = "Hub name must not be blank")
    @Schema(description = "Display name of the charging hub", example = "Bellandur Tech Park Hub")
    String name,

    @NotBlank(message = "Location ID must not be blank")
    @Schema(description = "Location Identifier", example = "BELLANDUR")
    String locationId,

    @NotNull(message = "Total chargers count must be specified")
    @Min(value = 1, message = "Total chargers must be at least 1")
    @Max(value = 200, message = "Total chargers cannot exceed 200")
    @Schema(description = "Number of physical charging plugs", example = "4")
    Integer totalChargers,

    @DecimalMin(value = "1.0", message = "Charger power must be at least 1.0 kW")
    @DecimalMax(value = "500.0", message = "Charger power cannot exceed 500.0 kW")
    @Schema(description = "Power output per charger plug (kW)", example = "50.0")
    Double chargerPowerKw,

    @DecimalMin(value = "0.0", message = "Tariff rate must be non-negative")
    @Schema(description = "Tariff rate per kWh in local currency", example = "8.50")
    Double tariffRate,

    @Schema(description = "Operational status (OPERATIONAL, OFFLINE, DEGRADED)", example = "OPERATIONAL")
    String status,

    @Schema(description = "Latitude of charging hub", example = "12.9352")
    Double latitude,

    @Schema(description = "Longitude of charging hub", example = "77.6245")
    Double longitude
) {
    public ChargerHubRequest(String id, String name, String locationId, Integer totalChargers) {
        this(id, name, locationId, totalChargers, 50.0, 8.50, "OPERATIONAL", 12.9716, 77.5946);
    }
}
