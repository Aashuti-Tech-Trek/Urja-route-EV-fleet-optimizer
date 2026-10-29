package com.moveinsync.evfleet.energy;

/**
 * Strategy interface defining energy consumption calculations for electric vehicles.
 *
 * <p>All energy units are in kilowatt-hours (kWh), distances in kilometres (km),
 * and consumption rates in kWh/km.
 */
public interface EnergyModel {

    /**
     * Calculates the effective energy consumption rate (kWh/km) given the base vehicle consumption
     * and external environmental factors (traffic, AC).
     *
     * @param baseConsumptionKwhPerKm base vehicle energy consumption in kWh/km, strictly positive
     * @param factors                 traffic and climate factors, not null
     * @return effective consumption in kWh/km
     */
    double calculateEffectiveConsumption(double baseConsumptionKwhPerKm, EnergyFactors factors);

    /**
     * Calculates total energy required (kWh) to traverse a given distance.
     *
     * @param distanceKm              distance in kilometres, non-negative
     * @param baseConsumptionKwhPerKm base vehicle energy consumption in kWh/km, strictly positive
     * @param factors                 traffic and climate factors, not null
     * @return energy required in kWh, non-negative and finite
     */
    double calculateEnergyRequired(double distanceKm, double baseConsumptionKwhPerKm, EnergyFactors factors);
}
