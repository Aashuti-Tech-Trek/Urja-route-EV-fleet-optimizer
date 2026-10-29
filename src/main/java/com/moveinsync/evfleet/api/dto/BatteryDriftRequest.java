package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record BatteryDriftRequest(
    @NotBlank String vehicleId,
    @DecimalMin("0.0") @DecimalMax("1.0") double observedSoc,
    @NotNull Instant eventTime
) {}
