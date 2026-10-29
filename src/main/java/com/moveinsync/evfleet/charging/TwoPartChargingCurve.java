package com.moveinsync.evfleet.charging;

import com.moveinsync.evfleet.exception.InvalidChargingSessionException;
import com.moveinsync.evfleet.validation.DomainValidator;

/**
 * Two-part piecewise linear charging curve approximation.
 *
 * <p><strong>Model Characteristics:</strong>
 * <ul>
 *   <li>Below 80% SoC (0.0 to 0.80): Fast charging at full rated charger power
 *       ({@code chargerPowerKw * fastRateMultiplier}).</li>
 *   <li>Above 80% SoC (0.80 to 1.00): Tapered charging at reduced power
 *       ({@code chargerPowerKw * taperMultiplier}), simulating the Constant Voltage (CV) saturation phase.</li>
 * </ul>
 *
 * <p><strong>Engineering Note:</strong>
 * This two-part model provides a computationally efficient approximation of real lithium-ion
 * charging curves suitable for mathematical optimization and scheduling. In future phases,
 * this can be substituted with higher-order polynomial or lookup-table battery models without
 * changing the {@link ChargingCurve} interface contract.
 */
public class TwoPartChargingCurve implements ChargingCurve {

    public static final double DEFAULT_SOC_THRESHOLD = 0.80;
    public static final double DEFAULT_FAST_RATE_MULTIPLIER = 1.0;
    public static final double DEFAULT_TAPER_MULTIPLIER = 0.50;

    private final double socThreshold;
    private final double fastRateMultiplier;
    private final double taperMultiplier;

    public TwoPartChargingCurve() {
        this(DEFAULT_SOC_THRESHOLD, DEFAULT_FAST_RATE_MULTIPLIER, DEFAULT_TAPER_MULTIPLIER);
    }

    public TwoPartChargingCurve(double socThreshold, double fastRateMultiplier, double taperMultiplier) {
        DomainValidator.requireInRange(socThreshold, 0.10, 0.95, "SoC threshold",
                InvalidChargingSessionException::new);
        DomainValidator.requirePositive(fastRateMultiplier, "Fast rate multiplier",
                InvalidChargingSessionException::new);
        DomainValidator.requirePositive(taperMultiplier, "Taper multiplier",
                InvalidChargingSessionException::new);

        this.socThreshold = socThreshold;
        this.fastRateMultiplier = fastRateMultiplier;
        this.taperMultiplier = taperMultiplier;
    }

    public double getSocThreshold() {
        return socThreshold;
    }

    public double getFastRateMultiplier() {
        return fastRateMultiplier;
    }

    public double getTaperMultiplier() {
        return taperMultiplier;
    }

    @Override
    public double calculateChargingTimeHours(double batteryCapacityKwh, double startSoc, double targetSoc, double chargerPowerKw) {
        validateInputs(batteryCapacityKwh, startSoc, targetSoc, chargerPowerKw);

        if (startSoc == targetSoc) {
            return 0.0;
        }

        double energyBelow = calculateEnergyBelowThreshold(batteryCapacityKwh, startSoc, targetSoc);
        double energyAbove = calculateEnergyAboveThreshold(batteryCapacityKwh, startSoc, targetSoc);

        double effectivePowerFast = chargerPowerKw * fastRateMultiplier;
        double effectivePowerTaper = chargerPowerKw * taperMultiplier;

        double timeFastHours = (effectivePowerFast > 0.0) ? (energyBelow / effectivePowerFast) : 0.0;
        double timeTaperHours = (effectivePowerTaper > 0.0) ? (energyAbove / effectivePowerTaper) : 0.0;

        return timeFastHours + timeTaperHours;
    }

    @Override
    public double calculateEnergyDeliveredKwh(double batteryCapacityKwh, double startSoc, double targetSoc) {
        validateInputs(batteryCapacityKwh, startSoc, targetSoc, 1.0);
        return batteryCapacityKwh * (targetSoc - startSoc);
    }

    /**
     * Calculates the portion of energy delivered below the 80% SoC threshold.
     */
    public double calculateEnergyBelowThreshold(double batteryCapacityKwh, double startSoc, double targetSoc) {
        validateInputs(batteryCapacityKwh, startSoc, targetSoc, 1.0);
        double lowerBound = Math.min(startSoc, socThreshold);
        double upperBound = Math.min(targetSoc, socThreshold);
        double deltaSocBelow = Math.max(0.0, upperBound - lowerBound);
        return batteryCapacityKwh * deltaSocBelow;
    }

    /**
     * Calculates the portion of energy delivered above the 80% SoC threshold.
     */
    public double calculateEnergyAboveThreshold(double batteryCapacityKwh, double startSoc, double targetSoc) {
        validateInputs(batteryCapacityKwh, startSoc, targetSoc, 1.0);
        double lowerBound = Math.max(startSoc, socThreshold);
        double upperBound = Math.max(targetSoc, socThreshold);
        double deltaSocAbove = Math.max(0.0, upperBound - lowerBound);
        return batteryCapacityKwh * deltaSocAbove;
    }

    @Override
    public double calculateResultingSoc(double batteryCapacityKwh, double startSoc, double chargingTimeHours, double chargerPowerKw) {
        DomainValidator.requirePositive(batteryCapacityKwh, "Battery capacity (kWh)",
                InvalidChargingSessionException::new);
        DomainValidator.requireInRange(startSoc, 0.0, 1.0, "Start state of charge",
                InvalidChargingSessionException::new);
        DomainValidator.requireNonNegative(chargingTimeHours, "Charging time (hours)",
                InvalidChargingSessionException::new);
        DomainValidator.requirePositive(chargerPowerKw, "Charger power (kW)",
                InvalidChargingSessionException::new);

        if (chargingTimeHours == 0.0 || startSoc >= 1.0) {
            return startSoc;
        }

        double effectivePowerFast = chargerPowerKw * fastRateMultiplier;
        double effectivePowerTaper = chargerPowerKw * taperMultiplier;

        double currentSoc = startSoc;

        // Phase 1: Fast charging up to threshold if startSoc < threshold
        if (currentSoc < socThreshold) {
            double deltaSocToThreshold = socThreshold - currentSoc;
            double energyToThreshold = batteryCapacityKwh * deltaSocToThreshold;
            double timeToThreshold = energyToThreshold / effectivePowerFast;

            if (chargingTimeHours <= timeToThreshold) {
                double energyAdded = chargingTimeHours * effectivePowerFast;
                return currentSoc + (energyAdded / batteryCapacityKwh);
            } else {
                currentSoc = socThreshold;
                chargingTimeHours -= timeToThreshold;
            }
        }

        // Phase 2: Tapered charging above threshold
        if (chargingTimeHours > 0.0 && currentSoc < 1.0) {
            double energyAdded = chargingTimeHours * effectivePowerTaper;
            double deltaSoc = energyAdded / batteryCapacityKwh;
            currentSoc = Math.min(1.0, currentSoc + deltaSoc);
        }

        return currentSoc;
    }

    private void validateInputs(double batteryCapacityKwh, double startSoc, double targetSoc, double chargerPowerKw) {
        DomainValidator.requirePositive(batteryCapacityKwh, "Battery capacity (kWh)",
                InvalidChargingSessionException::new);
        DomainValidator.requireInRange(startSoc, 0.0, 1.0, "Start state of charge",
                InvalidChargingSessionException::new);
        DomainValidator.requireInRange(targetSoc, 0.0, 1.0, "Target state of charge",
                InvalidChargingSessionException::new);
        DomainValidator.requirePositive(chargerPowerKw, "Charger power (kW)",
                InvalidChargingSessionException::new);

        if (targetSoc < startSoc) {
            throw new InvalidChargingSessionException(String.format(
                    "Target SoC (%.4f) cannot be less than starting SoC (%.4f)", targetSoc, startSoc));
        }
    }
}
