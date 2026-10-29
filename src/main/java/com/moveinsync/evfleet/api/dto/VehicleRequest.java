package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record VehicleRequest(
    @NotBlank String id,
    @DecimalMin("0.0") @DecimalMax("1.0") double currentSoc,
    @DecimalMin("1.0") double batteryCapacityKwh,
    @DecimalMin("0.01") double efficiencyKwhPerKm,
    @DecimalMin("1.0") double maxChargeRateKw,
    @NotBlank String currentLocationId,
    @NotNull Instant availableFrom
) {}
