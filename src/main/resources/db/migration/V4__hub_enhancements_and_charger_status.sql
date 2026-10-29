-- V4__hub_enhancements_and_charger_status.sql
-- Add power, tariff, and operational status to charger_hubs table
ALTER TABLE charger_hubs ADD COLUMN IF NOT EXISTS charger_power_kw DOUBLE PRECISION NOT NULL DEFAULT 50.0;
ALTER TABLE charger_hubs ADD COLUMN IF NOT EXISTS tariff_rate DOUBLE PRECISION NOT NULL DEFAULT 8.50;
ALTER TABLE charger_hubs ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'OPERATIONAL';
ALTER TABLE charger_hubs ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION NOT NULL DEFAULT 12.9716;
ALTER TABLE charger_hubs ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION NOT NULL DEFAULT 77.5946;

-- Create composite index on tenant_id and id
CREATE INDEX IF NOT EXISTS idx_charger_hubs_tenant_id ON charger_hubs(tenant_id, id);
