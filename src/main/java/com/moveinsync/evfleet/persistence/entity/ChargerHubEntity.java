package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "charger_hubs")
public class ChargerHubEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location_id", length = 64, nullable = false)
    private String locationId;

    @Column(name = "total_chargers", nullable = false)
    private int totalChargers;

    @Column(name = "charger_power_kw", nullable = false)
    private double chargerPowerKw = 50.0;

    @Column(name = "tariff_rate", nullable = false)
    private double tariffRate = 8.50;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "OPERATIONAL"; // "OPERATIONAL", "OFFLINE", "DEGRADED"

    @Column(name = "latitude", nullable = false)
    private double latitude = 12.9716;

    @Column(name = "longitude", nullable = false)
    private double longitude = 77.5946;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ChargerHubEntity() {}

    public ChargerHubEntity(String id, String tenantId, String name, String locationId, int totalChargers) {
        this(id, tenantId, name, locationId, totalChargers, 50.0, 8.50, "OPERATIONAL", 12.9716, 77.5946);
    }

    public ChargerHubEntity(String id, String tenantId, String name, String locationId, int totalChargers,
                            double chargerPowerKw, double tariffRate, String status, double latitude, double longitude) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.locationId = locationId;
        this.totalChargers = totalChargers;
        this.chargerPowerKw = chargerPowerKw > 0 ? chargerPowerKw : 50.0;
        this.tariffRate = tariffRate >= 0 ? tariffRate : 8.50;
        this.status = status != null ? status : "OPERATIONAL";
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }

    public int getTotalChargers() { return totalChargers; }
    public void setTotalChargers(int totalChargers) { this.totalChargers = totalChargers; }

    public double getChargerPowerKw() { return chargerPowerKw; }
    public void setChargerPowerKw(double chargerPowerKw) { this.chargerPowerKw = chargerPowerKw; }

    public double getTariffRate() { return tariffRate; }
    public void setTariffRate(double tariffRate) { this.tariffRate = tariffRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
