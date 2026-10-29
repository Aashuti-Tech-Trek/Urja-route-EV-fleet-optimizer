-- V2__add_idempotency_fingerprint.sql
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(128);
