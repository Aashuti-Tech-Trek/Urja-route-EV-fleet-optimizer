package com.moveinsync.evfleet.energy;

import com.moveinsync.evfleet.exception.InvalidEnergyCalculationException;
import com.moveinsync.evfleet.validation.DomainValidator;

/**
 * Baseline standard implementation of {@link EnergyModel}.
 *
 * <p><strong>Formulas:</strong>
 * <pre>
 *   effectiveConsumption = baseConsumption × trafficMultiplier × acMultiplier
 *   energyRequired = distanceKm × effectiveConsumption
 * </pre>
 */
public class StandardEnergyModel implements EnergyModel {

    @Override
    public double calculateEffectiveConsumption(double baseConsumptionKwhPerKm, EnergyFactors factors) {
        DomainValidator.requirePositive(baseConsumptionKwhPerKm, "Base consumption (kWh/km)",
                InvalidEnergyCalculationException::new);
        DomainValidator.requireNonNull(factors, "Energy factors",
                InvalidEnergyCalculationException::new);

        double effective = baseConsumptionKwhPerKm * factors.trafficMultiplier() * factors.acMultiplier();
        DomainValidator.requirePositive(effective, "Effective consumption",
                InvalidEnergyCalculationException::new);
        return effective;
    }

    @Override
    public double calculateEnergyRequired(double distanceKm, double baseConsumptionKwhPerKm, EnergyFactors factors) {
        DomainValidator.requireNonNegative(distanceKm, "Distance (km)",
                InvalidEnergyCalculationException::new);

        if (distanceKm == 0.0) {
            return 0.0;
        }

        double effectiveConsumption = calculateEffectiveConsumption(baseConsumptionKwhPerKm, factors);
        double energyRequired = distanceKm * effectiveConsumption;

        DomainValidator.requireNonNegative(energyRequired, "Energy required (kWh)",
                InvalidEnergyCalculationException::new);
        return energyRequired;
    }
}
