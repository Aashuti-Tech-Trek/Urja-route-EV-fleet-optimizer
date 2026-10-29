package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trips")
public class TripEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pickup_location_id", length = 64, nullable = false)
    private String pickupLocationId;

    @Column(name = "dropoff_location_id", length = 64, nullable = false)
    private String dropoffLocationId;

    @Column(name = "pickup_window_start", nullable = false)
    private Instant pickupWindowStart;

    @Column(name = "pickup_window_end", nullable = false)
    private Instant pickupWindowEnd;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    @Column(name = "passenger_count", nullable = false)
    private int passengerCount = 1;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public TripEntity() {}

    public TripEntity(String id, String tenantId, String pickupLocationId, String dropoffLocationId,
                      Instant pickupWindowStart, Instant pickupWindowEnd, Instant deadline,
                      double distanceKm, int passengerCount, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.pickupLocationId = pickupLocationId;
        this.dropoffLocationId = dropoffLocationId;
        this.pickupWindowStart = pickupWindowStart;
        this.pickupWindowEnd = pickupWindowEnd;
        this.deadline = deadline;
        this.distanceKm = distanceKm;
        this.passengerCount = passengerCount;
        this.status = status != null ? status : "PENDING";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPickupLocationId() { return pickupLocationId; }
    public void setPickupLocationId(String pickupLocationId) { this.pickupLocationId = pickupLocationId; }

    public String getDropoffLocationId() { return dropoffLocationId; }
    public void setDropoffLocationId(String dropoffLocationId) { this.dropoffLocationId = dropoffLocationId; }

    public Instant getPickupWindowStart() { return pickupWindowStart; }
    public void setPickupWindowStart(Instant pickupWindowStart) { this.pickupWindowStart = pickupWindowStart; }

    public Instant getPickupWindowEnd() { return pickupWindowEnd; }
    public void setPickupWindowEnd(Instant pickupWindowEnd) { this.pickupWindowEnd = pickupWindowEnd; }

    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }

    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }

    public int getPassengerCount() { return passengerCount; }
    public void setPassengerCount(int passengerCount) { this.passengerCount = passengerCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
