package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_assignments")
public class TripAssignmentEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "plan_proposal_id", length = 64, nullable = false)
    private String planProposalId;

    @Column(name = "vehicle_id", length = 64, nullable = false)
    private String vehicleId;

    @Column(name = "trip_id", length = 64, nullable = false)
    private String tripId;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_completion", nullable = false)
    private Instant scheduledCompletion;

    @Column(name = "arrival_soc", nullable = false)
    private double arrivalSoc;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    public TripAssignmentEntity() {}

    public TripAssignmentEntity(String id, String planProposalId, String vehicleId, String tripId,
                                Instant scheduledStart, Instant scheduledCompletion,
                                double arrivalSoc, int sequenceOrder) {
        this.id = id;
        this.planProposalId = planProposalId;
        this.vehicleId = vehicleId;
        this.tripId = tripId;
        this.scheduledStart = scheduledStart;
        this.scheduledCompletion = scheduledCompletion;
        this.arrivalSoc = arrivalSoc;
        this.sequenceOrder = sequenceOrder;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlanProposalId() { return planProposalId; }
    public void setPlanProposalId(String planProposalId) { this.planProposalId = planProposalId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public Instant getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(Instant scheduledStart) { this.scheduledStart = scheduledStart; }

    public Instant getScheduledCompletion() { return scheduledCompletion; }
    public void setScheduledCompletion(Instant scheduledCompletion) { this.scheduledCompletion = scheduledCompletion; }

    public double getArrivalSoc() { return arrivalSoc; }
    public void setArrivalSoc(double arrivalSoc) { this.arrivalSoc = arrivalSoc; }

    public int getSequenceOrder() { return sequenceOrder; }
    public void setSequenceOrder(int sequenceOrder) { this.sequenceOrder = sequenceOrder; }
}
