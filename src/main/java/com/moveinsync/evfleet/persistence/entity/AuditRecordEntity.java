package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_records")
public class AuditRecordEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "entity_type", length = 64, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, nullable = false)
    private String entityId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    public AuditRecordEntity() {}

    public AuditRecordEntity(String id, String tenantId, String action, String entityType,
                             String entityId, String details) {
        this.id = id;
        this.tenantId = tenantId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
