package com.moveinsync.evfleet.explain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable explanation for a single charging session, derived from the ChargingSession domain object.
 */
public final class ChargingExplanation {

    private final String sessionId;
    private final String vehicleId;
    private final String hubId;
    private final Instant startTime;
    private final Instant endTime;
    private final Duration duration;
    private final double startSoc;
    private final double endSoc;
    private final double energyDeliveredKwh;
    private final BigDecimal estimatedCost;
    private final String currency;
    private final String tariffPeriodLabel; // e.g., "PEAK" / "OFF_PEAK" — derived from session window
    private final String rationale;

    public ChargingExplanation(String sessionId, String vehicleId, String hubId,
                               Instant startTime, Instant endTime,
                               double startSoc, double endSoc,
                               double energyDeliveredKwh,
                               BigDecimal estimatedCost, String currency,
                               String tariffPeriodLabel, String rationale) {
        this.sessionId            = Objects.requireNonNull(sessionId, "sessionId");
        this.vehicleId            = Objects.requireNonNull(vehicleId, "vehicleId");
        this.hubId                = Objects.requireNonNull(hubId, "hubId");
        this.startTime            = Objects.requireNonNull(startTime, "startTime");
        this.endTime              = Objects.requireNonNull(endTime, "endTime");
        this.duration             = Duration.between(startTime, endTime);
        this.startSoc             = startSoc;
        this.endSoc               = endSoc;
        this.energyDeliveredKwh   = energyDeliveredKwh;
        this.estimatedCost        = Objects.requireNonNull(estimatedCost, "estimatedCost");
        this.currency             = Objects.requireNonNull(currency, "currency");
        this.tariffPeriodLabel    = tariffPeriodLabel != null ? tariffPeriodLabel : "UNKNOWN";
        this.rationale            = rationale != null ? rationale : "";
    }

    public String getSessionId()             { return sessionId; }
    public String getVehicleId()             { return vehicleId; }
    public String getHubId()                 { return hubId; }
    public Instant getStartTime()            { return startTime; }
    public Instant getEndTime()              { return endTime; }
    public Duration getDuration()            { return duration; }
    public double getStartSoc()              { return startSoc; }
    public double getEndSoc()                { return endSoc; }
    public double getEnergyDeliveredKwh()    { return energyDeliveredKwh; }
    public BigDecimal getEstimatedCost()     { return estimatedCost; }
    public String getCurrency()              { return currency; }
    public String getTariffPeriodLabel()     { return tariffPeriodLabel; }
    public String getRationale()             { return rationale; }

    @Override
    public String toString() {
        return String.format(
                "ChargingExplanation[session=%s, vehicle=%s, hub=%s, soc=%.2f->%.2f, %.2fkWh, %s %s, %s]",
                sessionId, vehicleId, hubId, startSoc, endSoc, energyDeliveredKwh,
                currency, estimatedCost, tariffPeriodLabel);
    }
}
