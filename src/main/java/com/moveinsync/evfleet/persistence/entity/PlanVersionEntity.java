package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "plan_versions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "version_number"})
})
public class PlanVersionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "version_number", nullable = false)
    private long versionNumber;

    @Column(name = "proposal_id", length = 64, nullable = false)
    private String proposalId;

    @Column(name = "status", length = 32, nullable = false)
    private String status; // ACCEPTED, SUPERSEDED, CANCELLED

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public PlanVersionEntity() {}

    public PlanVersionEntity(String id, String tenantId, long versionNumber, String proposalId,
                             String status, Instant acceptedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.versionNumber = versionNumber;
        this.proposalId = proposalId;
        this.status = status;
        this.acceptedAt = acceptedAt;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public long getVersionNumber() { return versionNumber; }
    public void setVersionNumber(long versionNumber) { this.versionNumber = versionNumber; }

    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
