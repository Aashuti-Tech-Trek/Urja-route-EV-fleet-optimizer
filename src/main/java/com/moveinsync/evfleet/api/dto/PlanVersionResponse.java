package com.moveinsync.evfleet.api.dto;

import java.time.Instant;

public record PlanVersionResponse(
    String id,
    String tenantId,
    long versionNumber,
    String proposalId,
    String status,
    Instant acceptedAt
) {}
