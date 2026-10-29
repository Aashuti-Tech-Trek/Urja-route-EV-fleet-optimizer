-- V1__initial_schema.sql
-- UrjaRoute PostgreSQL Database Schema

CREATE TABLE tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehicles (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    current_soc DOUBLE PRECISION NOT NULL,
    battery_capacity_kwh DOUBLE PRECISION NOT NULL,
    efficiency_kwh_per_km DOUBLE PRECISION NOT NULL,
    max_charge_rate_kw DOUBLE PRECISION NOT NULL,
    current_location_id VARCHAR(64) NOT NULL,
    available_from TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE charger_hubs (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    location_id VARCHAR(64) NOT NULL,
    total_chargers INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE charger_plugs (
    id VARCHAR(64) PRIMARY KEY,
    hub_id VARCHAR(64) NOT NULL REFERENCES charger_hubs(id) ON DELETE CASCADE,
    plug_number INT NOT NULL,
    max_power_kw DOUBLE PRECISION NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    UNIQUE (hub_id, plug_number)
);

CREATE TABLE trips (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    pickup_location_id VARCHAR(64) NOT NULL,
    dropoff_location_id VARCHAR(64) NOT NULL,
    pickup_window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    pickup_window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    distance_km DOUBLE PRECISION NOT NULL,
    passenger_count INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plan_proposals (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    planning_horizon_start TIMESTAMP WITH TIME ZONE NOT NULL,
    planning_horizon_end TIMESTAMP WITH TIME ZONE NOT NULL,
    total_cost DOUBLE PRECISION NOT NULL,
    service_rate DOUBLE PRECISION NOT NULL,
    is_valid BOOLEAN NOT NULL DEFAULT FALSE,
    validation_report_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plan_versions (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    version_number BIGINT NOT NULL,
    proposal_id VARCHAR(64) NOT NULL REFERENCES plan_proposals(id),
    status VARCHAR(32) NOT NULL, -- 'ACCEPTED', 'SUPERSEDED', 'CANCELLED'
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, version_number)
);

CREATE TABLE trip_assignments (
    id VARCHAR(64) PRIMARY KEY,
    plan_proposal_id VARCHAR(64) NOT NULL REFERENCES plan_proposals(id) ON DELETE CASCADE,
    vehicle_id VARCHAR(64) NOT NULL REFERENCES vehicles(id),
    trip_id VARCHAR(64) NOT NULL REFERENCES trips(id),
    scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_completion TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_soc DOUBLE PRECISION NOT NULL,
    sequence_order INT NOT NULL
);

CREATE TABLE charging_sessions (
    id VARCHAR(64) PRIMARY KEY,
    plan_proposal_id VARCHAR(64) NOT NULL REFERENCES plan_proposals(id) ON DELETE CASCADE,
    vehicle_id VARCHAR(64) NOT NULL REFERENCES vehicles(id),
    hub_id VARCHAR(64) NOT NULL REFERENCES charger_hubs(id),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    energy_added_kwh DOUBLE PRECISION NOT NULL,
    cost DOUBLE PRECISION NOT NULL
);

CREATE TABLE audit_records (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    details TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    response_payload TEXT NOT NULL,
    http_status INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehicles_tenant ON vehicles(tenant_id);
CREATE INDEX idx_trips_tenant ON trips(tenant_id);
CREATE INDEX idx_plan_versions_tenant ON plan_versions(tenant_id, version_number);
CREATE INDEX idx_trip_assignments_proposal ON trip_assignments(plan_proposal_id);
CREATE INDEX idx_charging_sessions_proposal ON charging_sessions(plan_proposal_id);
