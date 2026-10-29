package com.moveinsync.evfleet.api.dto;

import jakarta.validation.constraints.Min;

public record AcceptPlanRequest(
    @Min(value = 0, message = "expectedCurrentVersion must be non-negative")
    long expectedCurrentVersion
) {}
