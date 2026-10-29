package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidChargingSessionException;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an EV battery charging session.
 *
 * <p><strong>Engineering Boundary:</strong>
 * Full charger slot scheduling, queueing, and concurrency allocation will be implemented
 * in later phases. This model provides the authoritative domain record and invariant validation
 * for individual charging events.
 */
public class ChargingSession {

    private static final double CAPACITY_TOLERANCE_KWH = 1e-3;

    private final String sessionId;
    private final String vehicleId;
    private final String hubId;
    private final Instant startTime;
    private final Instant endTime;
    private final double startSoc;
    private final double endSoc;
    private final double energyDeliveredKwh;
    private final BigDecimal estimatedCost;
    private final String currency;

    public ChargingSession(String sessionId, String vehicleId, String hubId,
                           Instant startTime, Instant endTime,
                           double startSoc, double endSoc,
                           double energyDeliveredKwh, BigDecimal estimatedCost,
                           String currency) {
        this.sessionId = DomainValidator.requireNonBlank(sessionId, "Session ID", InvalidChargingSessionException::new);
        this.vehicleId = DomainValidator.requireNonBlank(vehicleId, "Vehicle ID", InvalidChargingSessionException::new);
        this.hubId = DomainValidator.requireNonBlank(hubId, "Hub ID", InvalidChargingSessionException::new);
        this.startTime = DomainValidator.requireNonNull(startTime, "Start time", InvalidChargingSessionException::new);
        this.endTime = DomainValidator.requireNonNull(endTime, "End time", InvalidChargingSessionException::new);
        this.startSoc = DomainValidator.requireInRange(startSoc, 0.0, 1.0, "Start SoC", InvalidChargingSessionException::new);
        this.endSoc = DomainValidator.requireInRange(endSoc, 0.0, 1.0, "End SoC", InvalidChargingSessionException::new);
        this.energyDeliveredKwh = DomainValidator.requireNonNegative(energyDeliveredKwh, "Energy delivered (kWh)", InvalidChargingSessionException::new);
        this.estimatedCost = DomainValidator.requireNonNull(estimatedCost, "Estimated cost", InvalidChargingSessionException::new);
        this.currency = DomainValidator.requireNonBlank(currency, "Currency", InvalidChargingSessionException::new).toUpperCase();

        if (!endTime.isAfter(startTime)) {
            throw new InvalidChargingSessionException(String.format(
                    "Session end time (%s) must be strictly after start time (%s)", endTime, startTime));
        }

        if (endSoc < startSoc) {
            throw new InvalidChargingSessionException(String.format(
                    "Ending SoC (%.4f) cannot be lower than starting SoC (%.4f)", endSoc, startSoc));
        }

        if (estimatedCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidChargingSessionException("Estimated cost cannot be negative, but was: " + estimatedCost);
        }
    }

    /**
     * Validates that the energy delivered during the session does not exceed the vehicle's
     * physical battery capacity for the observed delta SoC.
     *
     * @param batteryCapacityKwh total vehicle battery capacity in kWh
     * @throws InvalidChargingSessionException if energy delivered exceeds battery capacity limits
     */
    public void validateAgainstBatteryCapacity(double batteryCapacityKwh) {
        DomainValidator.requirePositive(batteryCapacityKwh, "Battery capacity (kWh)", InvalidChargingSessionException::new);
        double maxPhysicalEnergy = batteryCapacityKwh * (endSoc - startSoc);
        if (energyDeliveredKwh > (maxPhysicalEnergy + CAPACITY_TOLERANCE_KWH)) {
            throw new InvalidChargingSessionException(String.format(
                    "Energy delivered (%.3f kWh) exceeds physical battery capacity (%.3f kWh) for SoC delta [%.3f -> %.3f]",
                    energyDeliveredKwh, maxPhysicalEnergy, startSoc, endSoc));
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getHubId() {
        return hubId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    public double getStartSoc() {
        return startSoc;
    }

    public double getEndSoc() {
        return endSoc;
    }

    public double getDeltaSoc() {
        return endSoc - startSoc;
    }

    public double getEnergyDeliveredKwh() {
        return energyDeliveredKwh;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChargingSession that)) return false;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    @Override
    public String toString() {
        return String.format("ChargingSession[id=%s, vehicle=%s, hub=%s, soc=[%.2f->%.2f], energy=%.2fkWh, cost=%s %s]",
                sessionId, vehicleId, hubId, startSoc, endSoc, energyDeliveredKwh, currency, estimatedCost);
    }
}
