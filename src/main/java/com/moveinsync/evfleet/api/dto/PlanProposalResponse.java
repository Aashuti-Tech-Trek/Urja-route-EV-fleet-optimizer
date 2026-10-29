package com.moveinsync.evfleet.api.dto;

import java.time.Instant;
import java.util.List;

public record PlanProposalResponse(
    String proposalId,
    String tenantId,
    Instant planningHorizonStart,
    Instant planningHorizonEnd,
    double totalCost,
    double serviceRate,
    boolean valid,
    String validationReportJson,
    List<TripAssignmentDto> assignments,
    List<ChargingSessionDto> chargingSessions
) {
    public record TripAssignmentDto(
        String vehicleId,
        String tripId,
        Instant scheduledStart,
        Instant scheduledCompletion,
        double arrivalSoc,
        int sequenceOrder
    ) {}

    public record ChargingSessionDto(
        String vehicleId,
        String hubId,
        Instant startTime,
        Instant endTime,
        double energyAddedKwh,
        double cost
    ) {}
}
