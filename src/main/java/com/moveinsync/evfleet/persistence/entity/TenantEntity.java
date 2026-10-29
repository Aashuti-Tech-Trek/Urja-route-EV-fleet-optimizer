package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenants")
public class TenantEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public TenantEntity() {}

    public TenantEntity(String id, String name) {
        this.id = id;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
