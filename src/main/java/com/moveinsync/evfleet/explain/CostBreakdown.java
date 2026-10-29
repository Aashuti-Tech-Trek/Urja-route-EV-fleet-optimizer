package com.moveinsync.evfleet.explain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Transparent, immutable cost breakdown derived from the authoritative plan data.
 *
 * <p>Accounting rules:
 * <ul>
 *   <li>Total electricity cost = sum of all ChargingSession.estimatedCost values.</li>
 *   <li>costByHub subtotals reconcile with totalElectricityCost (subject to BigDecimal rounding).</li>
 *   <li>Backup cab monetary cost is NOT modelled — the count is reported separately.</li>
 *   <li>All monetary values use the plan's declared currency (e.g., "INR").</li>
 *   <li>Intermediate values maintained at full BigDecimal precision; no premature rounding.</li>
 * </ul>
 */
public final class CostBreakdown {

    private final BigDecimal totalElectricityCost;
    private final String currency;
    private final int totalChargingSessions;
    private final double totalEnergyChargedKwh;
    private final double totalEnergyConsumedKwh;
    private final Map<String, BigDecimal> costByHub;
    private final Map<String, BigDecimal> costByTariffPeriod;
    private final int backupCabTripCount;
    private final BigDecimal averageCostPerEvTrip; // null when evTripCount == 0
    private final int evTripCount;
    private final String backupCabCostNote;

    private CostBreakdown(Builder b) {
        this.totalElectricityCost  = Objects.requireNonNull(b.totalElectricityCost, "totalElectricityCost");
        this.currency              = Objects.requireNonNull(b.currency, "currency");
        this.totalChargingSessions = b.totalChargingSessions;
        this.totalEnergyChargedKwh = b.totalEnergyChargedKwh;
        this.totalEnergyConsumedKwh = b.totalEnergyConsumedKwh;
        this.costByHub             = b.costByHub != null ? Map.copyOf(b.costByHub) : Collections.emptyMap();
        this.costByTariffPeriod    = b.costByTariffPeriod != null ? Map.copyOf(b.costByTariffPeriod) : Collections.emptyMap();
        this.backupCabTripCount    = b.backupCabTripCount;
        this.evTripCount           = b.evTripCount;
        this.averageCostPerEvTrip  = b.evTripCount > 0
                ? b.totalElectricityCost.divide(BigDecimal.valueOf(b.evTripCount), 4, java.math.RoundingMode.HALF_UP)
                : null;
        this.backupCabCostNote     = "Backup cab monetary cost is not modelled. "
                + "Total fleet operating cost including petrol/diesel backup cabs is UNAVAILABLE.";
    }

    public BigDecimal getTotalElectricityCost()    { return totalElectricityCost; }
    public String getCurrency()                    { return currency; }
    public int getTotalChargingSessions()          { return totalChargingSessions; }
    public double getTotalEnergyChargedKwh()       { return totalEnergyChargedKwh; }
    public double getTotalEnergyConsumedKwh()      { return totalEnergyConsumedKwh; }
    public Map<String, BigDecimal> getCostByHub()  { return costByHub; }
    public Map<String, BigDecimal> getCostByTariffPeriod() { return costByTariffPeriod; }
    public int getBackupCabTripCount()             { return backupCabTripCount; }
    public int getEvTripCount()                    { return evTripCount; }
    /**
     * Average electricity cost per EV-assigned trip.
     * Returns null when no EV trips exist (division by zero is avoided).
     */
    public BigDecimal getAverageCostPerEvTrip()    { return averageCostPerEvTrip; }
    public String getBackupCabCostNote()           { return backupCabCostNote; }

    @Override
    public String toString() {
        return String.format(
                "CostBreakdown[totalElec=%s %s, sessions=%d, energyCharged=%.2fkWh, backup=%d trips]",
                currency, totalElectricityCost, totalChargingSessions, totalEnergyChargedKwh, backupCabTripCount);
    }

    public static final class Builder {
        private BigDecimal totalElectricityCost = BigDecimal.ZERO;
        private String currency = "INR";
        private int totalChargingSessions;
        private double totalEnergyChargedKwh;
        private double totalEnergyConsumedKwh;
        private Map<String, BigDecimal> costByHub;
        private Map<String, BigDecimal> costByTariffPeriod;
        private int backupCabTripCount;
        private int evTripCount;

        public Builder totalElectricityCost(BigDecimal v)          { totalElectricityCost = v; return this; }
        public Builder currency(String v)                          { currency = v; return this; }
        public Builder totalChargingSessions(int v)                { totalChargingSessions = v; return this; }
        public Builder totalEnergyChargedKwh(double v)             { totalEnergyChargedKwh = v; return this; }
        public Builder totalEnergyConsumedKwh(double v)            { totalEnergyConsumedKwh = v; return this; }
        public Builder costByHub(Map<String, BigDecimal> v)        { costByHub = v; return this; }
        public Builder costByTariffPeriod(Map<String, BigDecimal> v) { costByTariffPeriod = v; return this; }
        public Builder backupCabTripCount(int v)                   { backupCabTripCount = v; return this; }
        public Builder evTripCount(int v)                          { evTripCount = v; return this; }
        public CostBreakdown build()                               { return new CostBreakdown(this); }
    }
}
