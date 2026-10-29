package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record TripRequest(
    @NotBlank String id,
    @NotBlank String pickupLocationId,
    @NotBlank String dropoffLocationId,
    @NotNull Instant pickupWindowStart,
    @NotNull Instant pickupWindowEnd,
    @NotNull Instant deadline,
    @DecimalMin("0.1") double distanceKm,
    @Min(1) int passengerCount
) {}
