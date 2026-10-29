package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record GeneratePlanRequest(
    @NotNull Instant planningHorizonStart,
    @NotNull Instant planningHorizonEnd,
    List<String> vehicleIds,
    List<String> tripIds
) {}
