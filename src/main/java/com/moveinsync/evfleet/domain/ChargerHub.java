package com.moveinsync.evfleet.domain;

import com.moveinsync.evfleet.exception.InvalidChargerHubException;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.util.Objects;

/**
 * Domain entity representing an EV charging station / hub.
 *
 * <p><strong>Key Distinction:</strong>
 * {@code chargerCount} represents the physical number of distinct EVSE plugs/chargers available,
 * whereas {@code chargerPowerKw} is the power rating of an individual charger. Total potential
 * aggregate demand at full concurrency would be {@code chargerCount * chargerPowerKw}.
 */
public class ChargerHub {

    private final String hubId;
    private final String tenantId;
    private final Location location;
    private final int chargerCount;
    private final double chargerPowerKw;
    private final TariffSchedule tariffSchedule;

    public ChargerHub(String hubId, String tenantId, Location location,
                      int chargerCount, double chargerPowerKw, TariffSchedule tariffSchedule) {
        this.hubId = DomainValidator.requireNonBlank(hubId, "Hub ID", InvalidChargerHubException::new);
        this.tenantId = DomainValidator.requireNonBlank(tenantId, "Tenant ID", InvalidChargerHubException::new);
        this.location = DomainValidator.requireNonNull(location, "Hub location", InvalidChargerHubException::new);
        this.chargerCount = DomainValidator.requirePositive(chargerCount, "Charger count", InvalidChargerHubException::new);
        this.chargerPowerKw = DomainValidator.requirePositive(chargerPowerKw, "Charger power (kW)", InvalidChargerHubException::new);
        this.tariffSchedule = DomainValidator.requireNonNull(tariffSchedule, "Tariff schedule", InvalidChargerHubException::new);
    }

    public String getHubId() {
        return hubId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Location getLocation() {
        return location;
    }

    public int getChargerCount() {
        return chargerCount;
    }

    public double getChargerPowerKw() {
        return chargerPowerKw;
    }

    public TariffSchedule getTariffSchedule() {
        return tariffSchedule;
    }

    /**
     * Theoretical aggregate maximum power capacity of the hub if all chargers operate at 100%.
     */
    public double getTotalPotentialPowerKw() {
        return chargerCount * chargerPowerKw;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChargerHub that)) return false;
        return Objects.equals(hubId, that.hubId) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hubId, tenantId);
    }

    @Override
    public String toString() {
        return String.format("ChargerHub[id=%s, tenant=%s, chargers=%d, power=%.1fkW, loc=%s]",
                hubId, tenantId, chargerCount, chargerPowerKw, location);
    }
}
