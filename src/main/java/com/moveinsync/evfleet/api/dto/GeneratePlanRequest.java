package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record GeneratePlanRequest(
    @NotNull Instant planningHorizonStart,
    @NotNull Instant planningHorizonEnd,
    @Size(max = 1000, message = "Vehicle IDs list cannot exceed 1000 items")
    List<String> vehicleIds,
    @Size(max = 5000, message = "Trip IDs list cannot exceed 5000 items")
    List<String> tripIds
) {
    public GeneratePlanRequest {
        if (planningHorizonStart != null && planningHorizonEnd != null && !planningHorizonEnd.isAfter(planningHorizonStart)) {
            throw new IllegalArgumentException("planningHorizonEnd must be strictly after planningHorizonStart");
        }
    }
}
