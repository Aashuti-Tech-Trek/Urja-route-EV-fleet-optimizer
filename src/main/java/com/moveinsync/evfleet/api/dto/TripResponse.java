package com.moveinsync.evfleet.api.dto;

import java.time.Instant;

public record TripResponse(
    String id,
    String tenantId,
    String pickupLocationId,
    String dropoffLocationId,
    Instant pickupWindowStart,
    Instant pickupWindowEnd,
    Instant deadline,
    double distanceKm,
    int passengerCount,
    String status
) {}
