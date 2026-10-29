package com.moveinsync.evfleet.charging;

import java.time.Duration;

/**
 * Strategy interface modeling the electric vehicle battery charging curve.
 *
 * <p>Lithium-ion EV batteries typically exhibit non-linear charging dynamics: rapid charging
 * during the bulk Constant Current (CC) phase followed by reduced power absorption
 * in the Constant Voltage (CV) saturation phase (typically above 80% SoC).
 */
public interface ChargingCurve {

    /**
     * Calculates the time in hours required to charge from {@code startSoc} to {@code targetSoc}.
     *
     * @param batteryCapacityKwh total battery capacity in kWh, strictly positive
     * @param startSoc           starting state of charge in [0.0, 1.0]
     * @param targetSoc          target state of charge in [0.0, 1.0], &gt;= startSoc
     * @param chargerPowerKw     rated charger power in kW, strictly positive
     * @return required charging time in decimal hours
     */
    double calculateChargingTimeHours(double batteryCapacityKwh, double startSoc, double targetSoc, double chargerPowerKw);

    /**
     * Calculates the charging duration as a {@link Duration}.
     *
     * @param batteryCapacityKwh total battery capacity in kWh
     * @param startSoc           starting SoC
     * @param targetSoc          target SoC
     * @param chargerPowerKw     rated charger power in kW
     * @return required Duration
     */
    default Duration calculateChargingDuration(double batteryCapacityKwh, double startSoc, double targetSoc, double chargerPowerKw) {
        double hours = calculateChargingTimeHours(batteryCapacityKwh, startSoc, targetSoc, chargerPowerKw);
        long seconds = Math.round(hours * 3600.0);
        return Duration.ofSeconds(seconds);
    }

    /**
     * Calculates the total energy delivered to the battery in kWh between startSoc and targetSoc.
     *
     * @param batteryCapacityKwh total battery capacity in kWh
     * @param startSoc           starting SoC in [0.0, 1.0]
     * @param targetSoc          target SoC in [0.0, 1.0], &gt;= startSoc
     * @return energy delivered in kWh
     */
    double calculateEnergyDeliveredKwh(double batteryCapacityKwh, double startSoc, double targetSoc);

    /**
     * Calculates the resulting state of charge after charging for a given duration in hours.
     *
     * @param batteryCapacityKwh total battery capacity in kWh
     * @param startSoc           starting SoC in [0.0, 1.0]
     * @param chargingTimeHours  duration of charging in hours, non-negative
     * @param chargerPowerKw     rated charger power in kW, strictly positive
     * @return resulting SoC in [0.0, 1.0]
     */
    double calculateResultingSoc(double batteryCapacityKwh, double startSoc, double chargingTimeHours, double chargerPowerKw);
}
