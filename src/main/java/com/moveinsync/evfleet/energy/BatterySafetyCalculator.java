package com.moveinsync.evfleet.energy;

import com.moveinsync.evfleet.exception.InvalidEnergyCalculationException;
import com.moveinsync.evfleet.validation.DomainValidator;

/**
 * Reusable battery safety and reserve calculation engine.
 *
 * <p><strong>Core Invariants:</strong>
 * Every electric vehicle must maintain a minimum state-of-charge (SoC) reserve floor
 * of 15% (0.15) at all times to prevent deep discharge, battery degradation, and roadside stranding.
 *
 * <p><strong>Formulas:</strong>
 * <pre>
 *   minimumEnergy = 0.15 × batteryCapacityKwh
 *   currentEnergy = batteryCapacityKwh × stateOfCharge
 *   availableEnergyAboveReserve = currentEnergy - minimumEnergy
 * </pre>
 *
 * <p><strong>Conservative Rounding Policy:</strong>
 * Comparisons are strictly conservative: an SoC strictly below 0.15 (even by 1e-12)
 * is rejected as violating the safety floor.
 */
public final class BatterySafetyCalculator {

    /** Minimum required state-of-charge reserve ratio (15%). */
    public static final double MINIMUM_RESERVE_SOC = 0.15;

    private BatterySafetyCalculator() {
        // Utility class
    }

    /**
     * Calculates the minimum energy in kWh corresponding to the 15% safety reserve.
     *
     * @param batteryCapacityKwh total battery capacity in kWh, strictly positive
     * @return minimum reserve energy in kWh
     */
    public static double calculateMinimumReserveEnergy(double batteryCapacityKwh) {
        DomainValidator.requirePositive(batteryCapacityKwh, "Battery capacity (kWh)",
                InvalidEnergyCalculationException::new);
        return batteryCapacityKwh * MINIMUM_RESERVE_SOC;
    }

    /**
     * Calculates the current energy stored in the battery in kWh.
     *
     * @param batteryCapacityKwh total battery capacity in kWh, strictly positive
     * @param stateOfCharge       current SoC fraction in [0.0, 1.0]
     * @return current energy in kWh
     */
    public static double calculateCurrentEnergy(double batteryCapacityKwh, double stateOfCharge) {
        DomainValidator.requirePositive(batteryCapacityKwh, "Battery capacity (kWh)",
                InvalidEnergyCalculationException::new);
        DomainValidator.requireInRange(stateOfCharge, 0.0, 1.0, "State of charge",
                InvalidEnergyCalculationException::new);
        return batteryCapacityKwh * stateOfCharge;
    }

    /**
     * Calculates usable energy in kWh remaining above the 15% reserve floor.
     * If the vehicle is currently below the reserve, the returned value is negative.
     *
     * @param batteryCapacityKwh total battery capacity in kWh, strictly positive
     * @param stateOfCharge       current SoC fraction in [0.0, 1.0]
     * @return available energy in kWh above reserve (may be negative if already in reserve violation)
     */
    public static double calculateAvailableEnergyAboveReserve(double batteryCapacityKwh, double stateOfCharge) {
        double currentEnergy = calculateCurrentEnergy(batteryCapacityKwh, stateOfCharge);
        double minimumEnergy = calculateMinimumReserveEnergy(batteryCapacityKwh);
        return currentEnergy - minimumEnergy;
    }

    /**
     * Checks if the given state of charge strictly meets or exceeds the 15% safety reserve.
     * Conservative: no upward rounding is permitted.
     *
     * @param stateOfCharge SoC fraction in [0.0, 1.0]
     * @return true if stateOfCharge &gt;= 0.15, false otherwise
     */
    public static boolean isAboveOrAtReserve(double stateOfCharge) {
        DomainValidator.requireInRange(stateOfCharge, 0.0, 1.0, "State of charge",
                InvalidEnergyCalculationException::new);
        return stateOfCharge >= MINIMUM_RESERVE_SOC;
    }

    /**
     * Determines whether the vehicle can expend the specified energy (kWh) without dropping
     * below the 15% reserve floor.
     *
     * @param batteryCapacityKwh total battery capacity in kWh
     * @param stateOfCharge       current SoC fraction
     * @param energyRequiredKwh   energy required in kWh
     * @return true if the remaining energy after consumption is &gt;= 15% reserve
     */
    public static boolean canSustainTrip(double batteryCapacityKwh, double stateOfCharge, double energyRequiredKwh) {
        DomainValidator.requireNonNegative(energyRequiredKwh, "Energy required (kWh)",
                InvalidEnergyCalculationException::new);
        double available = calculateAvailableEnergyAboveReserve(batteryCapacityKwh, stateOfCharge);
        return available >= energyRequiredKwh;
    }

    /**
     * Calculates the maximum travel distance in kilometres a vehicle can cover before reaching
     * the 15% reserve floor.
     *
     * @param batteryCapacityKwh        battery capacity in kWh
     * @param stateOfCharge              current SoC fraction
     * @param effectiveConsumptionKwhPerKm effective energy consumption in kWh/km
     * @return maximum distance in km, 0.0 if already at or below reserve
     */
    public static double calculateMaxFeasibleDistanceKm(double batteryCapacityKwh, double stateOfCharge,
                                                       double effectiveConsumptionKwhPerKm) {
        DomainValidator.requirePositive(effectiveConsumptionKwhPerKm, "Effective consumption (kWh/km)",
                InvalidEnergyCalculationException::new);
        double availableEnergy = calculateAvailableEnergyAboveReserve(batteryCapacityKwh, stateOfCharge);
        if (availableEnergy <= 0.0) {
            return 0.0;
        }
        return availableEnergy / effectiveConsumptionKwhPerKm;
    }
}
