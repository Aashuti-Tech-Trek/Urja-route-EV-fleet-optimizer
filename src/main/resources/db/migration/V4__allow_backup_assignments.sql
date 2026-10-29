-- V4__allow_backup_assignments.sql
-- Allow vehicle_id in trip_assignments to be nullable for backup cab assignments
ALTER TABLE trip_assignments ALTER COLUMN vehicle_id DROP NOT NULL;
