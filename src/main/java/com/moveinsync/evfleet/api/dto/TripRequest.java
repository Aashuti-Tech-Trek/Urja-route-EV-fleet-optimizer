package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record TripRequest(
    @NotBlank @Size(max = 64) String id,
    @NotBlank @Size(max = 64) String pickupLocationId,
    @NotBlank @Size(max = 64) String dropoffLocationId,
    @NotNull Instant pickupWindowStart,
    @NotNull Instant pickupWindowEnd,
    @NotNull Instant deadline,
    @DecimalMin(value = "0.1", message = "distanceKm must be at least 0.1")
    @DecimalMax(value = "1000.0", message = "distanceKm cannot exceed 1000.0")
    double distanceKm,
    @Min(1) @Max(20) int passengerCount
) {
    public TripRequest {
        if (pickupWindowStart != null && pickupWindowEnd != null && !pickupWindowEnd.isAfter(pickupWindowStart)) {
            throw new IllegalArgumentException("pickupWindowEnd must be after pickupWindowStart");
        }
        if (pickupWindowEnd != null && deadline != null && deadline.isBefore(pickupWindowEnd)) {
            throw new IllegalArgumentException("deadline cannot be earlier than pickupWindowEnd");
        }
    }
}
