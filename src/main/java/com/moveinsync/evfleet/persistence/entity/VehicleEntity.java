package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "current_soc", nullable = false)
    private double currentSoc;

    @Column(name = "battery_capacity_kwh", nullable = false)
    private double batteryCapacityKwh;

    @Column(name = "efficiency_kwh_per_km", nullable = false)
    private double efficiencyKwhPerKm;

    @Column(name = "max_charge_rate_kw", nullable = false)
    private double maxChargeRateKw;

    @Column(name = "current_location_id", length = 64, nullable = false)
    private String currentLocationId;

    @Column(name = "available_from", nullable = false)
    private Instant availableFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public VehicleEntity() {}

    public VehicleEntity(String id, String tenantId, double currentSoc, double batteryCapacityKwh,
                         double efficiencyKwhPerKm, double maxChargeRateKw, String currentLocationId,
                         Instant availableFrom) {
        this.id = id;
        this.tenantId = tenantId;
        this.currentSoc = currentSoc;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.efficiencyKwhPerKm = efficiencyKwhPerKm;
        this.maxChargeRateKw = maxChargeRateKw;
        this.currentLocationId = currentLocationId;
        this.availableFrom = availableFrom;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public double getCurrentSoc() { return currentSoc; }
    public void setCurrentSoc(double currentSoc) { this.currentSoc = currentSoc; }

    public double getBatteryCapacityKwh() { return batteryCapacityKwh; }
    public void setBatteryCapacityKwh(double batteryCapacityKwh) { this.batteryCapacityKwh = batteryCapacityKwh; }

    public double getEfficiencyKwhPerKm() { return efficiencyKwhPerKm; }
    public void setEfficiencyKwhPerKm(double efficiencyKwhPerKm) { this.efficiencyKwhPerKm = efficiencyKwhPerKm; }

    public double getMaxChargeRateKw() { return maxChargeRateKw; }
    public void setMaxChargeRateKw(double maxChargeRateKw) { this.maxChargeRateKw = maxChargeRateKw; }

    public String getCurrentLocationId() { return currentLocationId; }
    public void setCurrentLocationId(String currentLocationId) { this.currentLocationId = currentLocationId; }

    public Instant getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(Instant availableFrom) { this.availableFrom = availableFrom; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
