package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "plan_proposals")
public class PlanProposalEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "planning_horizon_start", nullable = false)
    private Instant planningHorizonStart;

    @Column(name = "planning_horizon_end", nullable = false)
    private Instant planningHorizonEnd;

    @Column(name = "total_cost", nullable = false)
    private double totalCost;

    @Column(name = "service_rate", nullable = false)
    private double serviceRate;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @Column(name = "validation_report_json", columnDefinition = "TEXT")
    private String validationReportJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public PlanProposalEntity() {}

    public PlanProposalEntity(String id, String tenantId, Instant planningHorizonStart,
                              Instant planningHorizonEnd, double totalCost, double serviceRate,
                              boolean valid, String validationReportJson) {
        this.id = id;
        this.tenantId = tenantId;
        this.planningHorizonStart = planningHorizonStart;
        this.planningHorizonEnd = planningHorizonEnd;
        this.totalCost = totalCost;
        this.serviceRate = serviceRate;
        this.valid = valid;
        this.validationReportJson = validationReportJson;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Instant getPlanningHorizonStart() { return planningHorizonStart; }
    public void setPlanningHorizonStart(Instant planningHorizonStart) { this.planningHorizonStart = planningHorizonStart; }

    public Instant getPlanningHorizonEnd() { return planningHorizonEnd; }
    public void setPlanningHorizonEnd(Instant planningHorizonEnd) { this.planningHorizonEnd = planningHorizonEnd; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public double getServiceRate() { return serviceRate; }
    public void setServiceRate(double serviceRate) { this.serviceRate = serviceRate; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getValidationReportJson() { return validationReportJson; }
    public void setValidationReportJson(String validationReportJson) { this.validationReportJson = validationReportJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
