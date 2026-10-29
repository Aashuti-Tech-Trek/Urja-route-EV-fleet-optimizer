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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ChargerHubEntity() {}

    public ChargerHubEntity(String id, String tenantId, String name, String locationId, int totalChargers) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.locationId = locationId;
        this.totalChargers = totalChargers;
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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
