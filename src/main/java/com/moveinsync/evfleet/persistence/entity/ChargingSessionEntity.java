package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "charging_sessions")
public class ChargingSessionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "plan_proposal_id", length = 64, nullable = false)
    private String planProposalId;

    @Column(name = "vehicle_id", length = 64, nullable = false)
    private String vehicleId;

    @Column(name = "hub_id", length = 64, nullable = false)
    private String hubId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "energy_added_kwh", nullable = false)
    private double energyAddedKwh;

    @Column(name = "cost", nullable = false)
    private double cost;

    public ChargingSessionEntity() {}

    public ChargingSessionEntity(String id, String planProposalId, String vehicleId, String hubId,
                                 Instant startTime, Instant endTime, double energyAddedKwh, double cost) {
        this.id = id;
        this.planProposalId = planProposalId;
        this.vehicleId = vehicleId;
        this.hubId = hubId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.energyAddedKwh = energyAddedKwh;
        this.cost = cost;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlanProposalId() { return planProposalId; }
    public void setPlanProposalId(String planProposalId) { this.planProposalId = planProposalId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getHubId() { return hubId; }
    public void setHubId(String hubId) { this.hubId = hubId; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public double getEnergyAddedKwh() { return energyAddedKwh; }
    public void setEnergyAddedKwh(double energyAddedKwh) { this.energyAddedKwh = energyAddedKwh; }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }
}
