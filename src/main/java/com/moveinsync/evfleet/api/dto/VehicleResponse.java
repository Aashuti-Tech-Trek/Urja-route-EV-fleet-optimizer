package com.moveinsync.evfleet.api.dto;

import java.time.Instant;

public record VehicleResponse(
    String id,
    String tenantId,
    double currentSoc,
    double batteryCapacityKwh,
    double efficiencyKwhPerKm,
    double maxChargeRateKw,
    String currentLocationId,
    Instant availableFrom
) {}
