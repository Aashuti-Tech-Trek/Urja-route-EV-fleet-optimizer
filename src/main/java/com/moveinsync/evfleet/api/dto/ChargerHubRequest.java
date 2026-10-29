package com.moveinsync.evfleet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    Integer totalChargers
) {}
