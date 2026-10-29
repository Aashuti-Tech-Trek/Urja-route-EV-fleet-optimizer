package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ChargerFailureRequest(
    @NotBlank String hubId,
    @NotNull Instant outageStart,
    @NotNull Instant estimatedRestoration
) {
    public ChargerFailureRequest {
        if (outageStart != null && estimatedRestoration != null && !estimatedRestoration.isAfter(outageStart)) {
            throw new IllegalArgumentException("estimatedRestoration must be strictly after outageStart");
        }
    }
}
