-- V3__add_audit_actor_and_correlation.sql
ALTER TABLE audit_records ADD COLUMN IF NOT EXISTS actor_id VARCHAR(128);
ALTER TABLE audit_records ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
