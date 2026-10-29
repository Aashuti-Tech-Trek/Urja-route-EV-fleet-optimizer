package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record VehicleRequest(
    @NotBlank @Size(max = 64) String id,
    @DecimalMin("0.0") @DecimalMax("1.0") double currentSoc,
    @DecimalMin("5.0") @DecimalMax("300.0") double batteryCapacityKwh,
    @DecimalMin("0.05") @DecimalMax("1.0") double efficiencyKwhPerKm,
    @DecimalMin("5.0") @DecimalMax("350.0") double maxChargeRateKw,
    @NotBlank @Size(max = 64) String currentLocationId,
    @NotNull Instant availableFrom
) {
    public VehicleRequest {
        if (Double.isNaN(currentSoc) || Double.isInfinite(currentSoc)) {
            throw new IllegalArgumentException("currentSoc must be a finite number");
        }
        if (Double.isNaN(batteryCapacityKwh) || Double.isInfinite(batteryCapacityKwh)) {
            throw new IllegalArgumentException("batteryCapacityKwh must be a finite number");
        }
    }
}
