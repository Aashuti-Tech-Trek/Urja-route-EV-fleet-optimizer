package com.moveinsync.evfleet.api.dto;

import java.util.List;

public record ReplanningApiResponse(
    String outcome,
    String proposalId,
    double totalCost,
    double costDelta,
    List<String> changedVehicleAssignments,
    List<String> preservedVehicleAssignments,
    long replanDurationMs,
    boolean valid
) {}
