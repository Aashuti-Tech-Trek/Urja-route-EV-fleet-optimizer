package com.moveinsync.evfleet.energy;

import com.moveinsync.evfleet.exception.InvalidEnergyCalculationException;
import com.moveinsync.evfleet.validation.DomainValidator;

/**
 * Immutable value object holding contextual factors that affect vehicle energy consumption.
 *
 * <p><strong>Assumptions &amp; Illustrative Parameters:</strong>
 * <ul>
 *   <li>Standard baseline: trafficMultiplier = 1.0 (free-flow traffic), acMultiplier = 1.0 (ambient weather, AC off).</li>
 *   <li>Moderate traffic: trafficMultiplier = 1.15 to 1.25.</li>
 *   <li>Heavy evening shift traffic: trafficMultiplier = 1.30 to 1.45.</li>
 *   <li>Air conditioning in peak summer: acMultiplier = 1.15 to 1.25.</li>
 * </ul>
 */
public record EnergyFactors(double trafficMultiplier, double acMultiplier) {

    public static final double MIN_MULTIPLIER = 1.0;
    public static final double MAX_MULTIPLIER = 5.0;

    public EnergyFactors {
        DomainValidator.requireInRange(trafficMultiplier, MIN_MULTIPLIER, MAX_MULTIPLIER, "Traffic multiplier",
                InvalidEnergyCalculationException::new);
        DomainValidator.requireInRange(acMultiplier, MIN_MULTIPLIER, MAX_MULTIPLIER, "AC multiplier",
                InvalidEnergyCalculationException::new);
    }

    public static EnergyFactors standard() {
        return new EnergyFactors(1.0, 1.0);
    }

    public static EnergyFactors of(double trafficMultiplier, double acMultiplier) {
        return new EnergyFactors(trafficMultiplier, acMultiplier);
    }
}
