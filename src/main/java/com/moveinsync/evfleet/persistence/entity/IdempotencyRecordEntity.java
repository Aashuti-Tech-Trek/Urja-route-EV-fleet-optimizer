package com.moveinsync.evfleet.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "request_fingerprint", length = 128)
    private String requestFingerprint;

    @Column(name = "response_payload", columnDefinition = "TEXT", nullable = false)
    private String responsePayload;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public IdempotencyRecordEntity() {}

    public IdempotencyRecordEntity(String idempotencyKey, String tenantId, String requestFingerprint,
                                   String responsePayload, int httpStatus) {
        this.idempotencyKey = idempotencyKey;
        this.tenantId = tenantId;
        this.requestFingerprint = requestFingerprint;
        this.responsePayload = responsePayload;
        this.httpStatus = httpStatus;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }

    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
