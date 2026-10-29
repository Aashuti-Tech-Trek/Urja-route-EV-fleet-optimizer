package com.moveinsync.evfleet.api.dto;

import java.time.Instant;

public record AcceptPlanRequest(
    long expectedCurrentVersion
) {}
