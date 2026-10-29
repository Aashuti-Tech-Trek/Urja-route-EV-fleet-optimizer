# MoveInSync EV Fleet Charging & Trip Assignment Optimizer

> **MoveInSync Backend Case Study 2026 — Advanced Track (Case Study 6)**
> Enterprise-grade EV fleet optimizer for corporate commute operations.

---

## 1. Project Overview

The **MoveInSync EV Fleet Optimizer (UrjaRoute)** is an algorithmic planning and execution system designed to optimize electric cab assignments and charging schedules for high-throughput corporate commutes.

### Eventual Scale Target:
- **300 Electric Vehicles** across corporate fleet operations.
- **40 Chargers** distributed across strategic charging hubs.
- **1,500 Employee Trips** in a single evening shift window.
- **Full Planning Target:** Under 30 seconds for 1,500 trips.
- **Live Replanning Target:** Under 2 seconds upon telemetry battery drift or charger failure.

### Strict Optimization Hierarchy:
1. **Never violate hard operational constraints** (15% battery reserve floor, non-overlapping timelines, punctuality deadlines, charger hub capacity).
2. **Minimize trips requiring backup petrol or diesel cabs** (Uncompromised service reliability).
3. **Minimize total electricity cost** among candidate plans with identical backup counts via tariff-aware charging.
4. **Deterministic tie-breaking** by vehicle and trip identifiers.

---

## 2. Current Implementation Status

### Phase 1: Domain Foundation & Core Calculations (COMPLETE)
- **Geographic Domain (`location`):** Immutable `Location` value object with Haversine distance and spherical boundary validation.
- **Time and Tariffs (`time`):** Half-open `TimeWindow` `[start, end)`, `TariffSchedule`, `TariffSlot`, `TariffRate` supporting midnight-crossing windows and DST handling.
- **Energy Model (`energy`):** `EnergyModel`, `StandardEnergyModel`, and `BatterySafetyCalculator` enforcing a strict **15% state-of-charge reserve floor**.
- **Charging Dynamics (`charging`):** `TwoPartChargingCurve` modeling Constant Current (CC <80%) fast charging and Constant Voltage (CV >80%) taper charging.
- **Domain Aggregates (`domain`):** Invariant-enforcing models for `Vehicle`, `Trip`, `ChargerHub`, and `ChargingSession`.

### Phase 2: Trip Assignment & Charging Optimization Engine (COMPLETE)
- **Travel Foundation (`travel`):**
  - `TravelConfig`: Immutable configuration object exposing road-distance factor (default 1.3) and average driving speed (default 25.0 km/h) with boundary validation.
  - `TravelTimeEstimator` & `DefaultTravelTimeEstimator`: Replaceable interface estimating travel duration and road routing distance.
  - `DistanceMatrix`: Lazily-populated, memory-bounded, thread-safe cache for location-pair road distances.
- **Feasibility & Diagnostics (`planner`):**
  - `TripFeasibilityChecker`: 5-step evaluation returning structured `FeasibilityResult` with diagnostic slack, SoC projections, and machine-readable `FeasibilityReason` codes (`PICKUP_UNREACHABLE`, `DEADLINE_INFEASIBLE`, `INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE`, `INSUFFICIENT_ENERGY_FOR_TRIP`, `VEHICLE_UNAVAILABLE`, `CHARGER_CAPACITY_UNAVAILABLE`).
- **Scheduling & Simulation (`planner`):**
  - `VehicleTimeline` & `TimelineEvent`: Chronological sequence of empty travel, passenger trips, and charging sessions enforcing spatial continuity and the 15% reserve floor.
  - `ChargerOccupancyTracker`: Tracks concurrent hub usage to strictly prevent oversubscription beyond physical charger counts.
  - `ChargingScheduler`: Schedules cost-optimal charging opportunities using `TwoPartChargingCurve` and `TariffSchedule`.
- **Greedy Fleet Planner & Local Search (`planner`):**
  - `GreedyFleetPlanner`: Deterministic greedy construction heuristic sorting trips by urgency and difficulty, selecting EV candidates lexicographically, and evaluating charging opportunities when batteries are low.
  - `LocalSearchConfig`: Configurable budget for bounded local search improvements.
  - `FleetPlanResult`: Inspectable plan with per-vehicle timelines, charging sessions, cost breakdowns, and explainable backup cab assignments.
  - `PlanningQualityReport`: Machine-readable summary of trips requested, EV coverage percentage, costs, runtime metrics, and defensible lower bounds.
  - `FleetPlanValidator`: Independent validator verifying all hard constraints on completed plans.

### Independent Plan Validator & Safety Verification Engine (`validator`)
- **Independent Safety Certification:**
  - `PlanValidator`: Uncompromising public contract `ValidationReport validate(FleetPlanResult plan, PlanningInput input)` verifying generated plans without trusting planner flags or intermediate scores.
  - `DefaultPlanValidator`: Enterprise validator implementation executing 4 independent verification pillars:
    1. **Battery Reserve & SoC Invariants:** Verifies 15% reserve floor at every event, $\le 100\%$ capacity limits, and reconciles energy deltas with battery capacity ($|SoC_{end} - (SoC_{start} \pm \Delta E / C)| \le 10^{-4}$).
    2. **Trip Coverage & Temporal Continuity:** Enforces 100% trip accounting (all requested trips either assigned to exactly one EV or designated as backup), non-overlapping vehicle timelines, pickup punctuality, arrival before deadlines, and spatial continuity between consecutive destinations and origins.
    3. **Charger Hub Concurrency:** Uses an $O(N \log N)$ interval sweep-line algorithm with strict half-open $[start, end)$ semantics to guarantee active concurrent sessions $\le$ physical charger count at every hub instant, and strictly prohibits charging while carrying passengers.
    4. **Tariff Cost Reconciliation:** Re-evaluates energy cost per charging session independently against active `TariffSchedule` slots and validates aggregate plan cost integrity.
  - `ValidationReport`, `PlanViolation`, `ViolationCode`, `ValidationStatus`: Immutable diagnostic reporting with machine-readable violation codes and exact timestamps, entity IDs, and measured vs. expected metrics.

### Phase 4: Plan Explainability, Cost Breakdown, Quality Metrics & Lower-Bound Analysis (COMPLETE)
- **Structured Decision Explanations (`explain`):**
  - `AssignmentExplanation`: Detailed breakdown of each trip assignment (decision type `EV_ASSIGNED` vs `BACKUP_ASSIGNED`, assigned vehicle, empty-drive distance & energy, SoC progression before/after, pickup arrival time, charging dependencies, and candidate alternatives evaluated).
  - `ChargingExplanation`: Session-level justification including hub name, charger power, start/end SoC, duration, kWh delivered, tariff slots spanned, and electricity cost.
  - `AlternativeEvaluation` & `AlternativeOutcome`: Transparent audit trail of non-selected vehicle candidates showing specific feasibility rejection reasons without fabricating outcomes.
- **Cost Breakdown & Accounting (`explain`):**
  - `CostBreakdown`: Verifiable electricity cost breakdown with hub-level and tariff-period subtotals, energy consumption totals, average cost per EV trip, and explicitly noted unmodelled backup cab costs.
- **Plan Quality & Health Metrics (`explain`):**
  - `PlanQualityReport`: Consolidated audit report encompassing EV coverage percentage, fleet utilization, charger concurrency peaks, minimum observed SoC, independent validator verification status, and optimizer runtime.
- **Lower-Bound Benchmarking (`explain`):**
  - `LowerBoundResult` & `LowerBoundStatus`: Sound mathematical lower bounds for backup-trip count and electricity charging cost with explicit assumptions and gap metrics, clearly differentiating heuristic solutions from theoretical lower bounds.
- **Service Orchestration (`explain`):**
  - `PlanExplanationService`: Zero-mutation, deterministic service generating comprehensive `PlanExplanation` records from inputs, fleet plan results, and independent validation reports.

### Phase 5: Live Replanning & Failure Recovery (COMPLETE)
- **Real-Time Telemetry & Event Handling (`replan`):**
  - `BatteryDriftEvent`: Models real-world telemetry battery SoC deviations with validation $[0.0, 1.0]$ and stale event filtering.
  - `ChargerFailureEvent`: Models unexpected charger outages with half-open $[start, end)$ intervals and recovery awareness.
- **Bounded Incremental Repair Engine (`replan`):**
  - `ReplanningService`: High-performance replanning engine targeting $< 2.0\text{s}$ SLA with strict preservation of completed past trips.
  - `ReplanningResult` & `ReplanningOutcome`: Diagnostic replanning audit recording preserved/changed assignments, charging session shifts, backup delta, and runtime metrics.
  - Re-evaluates all generated replacement proposals through independent `PlanValidator`.

---

## 3. Directory Structure

```
MoveInSync_EV_Fleet/
├── .mvn/
│   └── wrapper/
│       ├── maven-wrapper.jar
│       └── maven-wrapper.properties
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── moveinsync/
│   │               └── evfleet/
│   │                   ├── EvFleetApplication.java
│   │                   ├── charging/
│   │                   │   ├── ChargingCurve.java
│   │                   │   └── TwoPartChargingCurve.java
│   │                   ├── domain/
│   │                   │   ├── ChargerHub.java
│   │                   │   ├── ChargingSession.java
│   │                   │   ├── Trip.java
│   │                   │   ├── Vehicle.java
│   │                   │   ├── VehicleAvailability.java
│   │                   │   ├── VehicleSpecification.java
│   │                   │   ├── VehicleState.java
│   │                   │   └── VehicleStatus.java
│   │                   ├── energy/
│   │                   │   ├── BatterySafetyCalculator.java
│   │                   │   ├── EnergyFactors.java
│   │                   │   ├── EnergyModel.java
│   │                   │   └── StandardEnergyModel.java
│   │                   ├── exception/
│   │                   │   ├── EvFleetException.java
│   │                   │   ├── InvalidChargerHubException.java
│   │                   │   ├── InvalidChargingSessionException.java
│   │                   │   ├── InvalidDomainException.java
│   │                   │   ├── InvalidEnergyCalculationException.java
│   │                   │   ├── InvalidLocationException.java
│   │                   │   ├── InvalidTariffException.java
│   │                   │   ├── InvalidTripException.java
│   │                   │   └── InvalidVehicleException.java
│   │                   ├── explain/
│   │                   │   ├── AlternativeEvaluation.java
│   │                   │   ├── AlternativeOutcome.java
│   │                   │   ├── AssignmentExplanation.java
│   │                   │   ├── ChargingExplanation.java
│   │                   │   ├── CostBreakdown.java
│   │                   │   ├── LowerBoundResult.java
│   │                   │   ├── LowerBoundStatus.java
│   │                   │   ├── PlanExplanation.java
│   │                   │   ├── PlanExplanationService.java
│   │                   │   └── PlanQualityReport.java
│   │                   ├── location/
│   │                   │   └── Location.java
│   │                   ├── planner/
│   │                   │   ├── ChargerOccupancyTracker.java
│   │                   │   ├── ChargingScheduler.java
│   │                   │   ├── FeasibilityReason.java
│   │                   │   ├── FeasibilityResult.java
│   │                   │   ├── FeasibilityStatus.java
│   │                   │   ├── FleetPlanResult.java
│   │                   │   ├── FleetPlanner.java
│   │                   │   ├── FleetPlanValidator.java
│   │                   │   ├── GreedyFleetPlanner.java
│   │                   │   ├── LocalSearchConfig.java
│   │                   │   ├── PlanningInput.java
│   │                   │   ├── PlanningQualityReport.java
│   │                   │   ├── ScheduledChargingPlan.java
│   │                   │   ├── TimelineEvent.java
│   │                   │   ├── TripAssignment.java
│   │                   │   ├── TripFeasibilityChecker.java
│   │                   │   └── VehicleTimeline.java
│   │                   ├── replan/
│   │                   │   ├── BatteryDriftEvent.java
│   │                   │   ├── ChargerFailureEvent.java
│   │                   │   ├── ReplanningOutcome.java
│   │                   │   ├── ReplanningResult.java
│   │                   │   └── ReplanningService.java
│   │                   ├── time/
│   │                   │   ├── TariffRate.java
│   │                   │   ├── TariffSchedule.java
│   │                   │   ├── TariffSlot.java
│   │                   │   ├── TariffType.java
│   │                   │   └── TimeWindow.java
│   │                   ├── travel/
│   │                   │   ├── DefaultTravelTimeEstimator.java
│   │                   │   ├── DistanceMatrix.java
│   │                   │   ├── TravelConfig.java
│   │                   │   └── TravelTimeEstimator.java
│   │                   ├── validation/
│   │                   │   └── DomainValidator.java
│   │                   └── validator/
│   │                       ├── DefaultPlanValidator.java
│   │                       ├── PlanValidationException.java
│   │                       ├── PlanValidator.java
│   │                       ├── PlanViolation.java
│   │                       ├── ValidationReport.java
│   │                       ├── ValidationStatus.java
│   │                       └── ViolationCode.java
│   └── test/
│       └── java/
│           └── com/
│               └── moveinsync/
│                   └── evfleet/
│                       ├── EvFleetApplicationTests.java
│                       ├── charging/
│                       │   └── TwoPartChargingCurveTest.java
│                       ├── domain/
│                       │   ├── ChargerHubTest.java
│                       │   ├── ChargingSessionTest.java
│                       │   ├── TripTest.java
│                       │   └── VehicleTest.java
│                       ├── energy/
│                       │   ├── BatterySafetyCalculatorTest.java
│                       │   └── StandardEnergyModelTest.java
│                       ├── location/
│                       │   └── LocationTest.java
│                       ├── planner/
│                       │   ├── GreedyFleetPlannerTest.java
│                       │   ├── Phase2QualityGateAuditTest.java
│                       │   └── TripFeasibilityCheckerTest.java
│                       ├── time/
│                       │   ├── TariffScheduleTest.java
│                       │   └── TimeWindowTest.java
│                       ├── travel/
│                       │   └── TravelTests.java
│                       └── validator/
│                           ├── PlanValidatorBenchmarkTest.java
│                           └── PlanValidatorTest.java
├── DESIGN.md
├── pom.xml
└── README.md
```

---

## 4. Build and Test Instructions

### Compile the Project:
```bash
mvn clean compile
```

### Run All Automated Unit & Integration Tests:
```bash
mvn test
```

### Phase 10: Hub Management & Tenant Operations (COMPLETE)
- **Hub Management APIs (`/api/v1/hubs`):** Full CRUD for charger hubs, paginated listing (`PageResponse<T>`), detail retrieval, power and tariff updates, status toggling (`OPERATIONAL`, `OFFLINE`, `DEGRADED`), and live utilization inspection (`/utilization`).
- **Strict Tenant Isolation:** All hub queries, updates, and planning operations restricted to the authenticated tenant. Cross-tenant access attempts return RFC 7807 `400 Bad Request` or `404 Not Found` without disclosing foreign tenant data.
- **Capacity & Charger Correctness:** Offline/failed hubs are dynamically excluded from planning and validation. Charger sweep-line ensures physical concurrent sessions do not exceed available plugs.
- **Tenant Overview Reconciled (`/api/v1/overview`):** Provides real-time reconciliation of total/available/offline chargers, pending/assigned/backup trips, and recent recovery events without hardcoded demo figures.

### Phase 11: Live Recovery & Operator Workflows (COMPLETE)
- **Telemetry Battery-Drift Recovery (`/api/v1/replanning/battery-drift`):** Bounded incremental replan (< 2.0s SLA) freezing completed trips, repositioning the vehicle at observed SoC, validating with `PlanValidator`, persisting updated SoC, and emitting auditable recovery events.
- **Charger-Failure Recovery (`/api/v1/replanning/charger-failure`):** Identifies impacted charging sessions during outage intervals, marks hub `OFFLINE`, replans around available operational hubs, and records audit logs.
- **Explicit Charger Restoration (`/api/v1/replanning/charger-restore/{hubId}`):** Safely restores repaired charging hubs to `OPERATIONAL` status and triggers an immediate fleet re-evaluation.
- **Cross-Tenant Incident Guard:** Validates vehicle and hub ownership before executing drift or failure workflows, rejecting unauthorized attempts.

### Phase 12: Deployment, Documentation & Delivery (COMPLETE)
- **One-Command Docker Compose:** Complete multi-service setup (`docker compose up --build -d`) with PostgreSQL 16 healthchecks, multi-stage non-root container, and Flyway database migrations (`V1` through `V4`).
- **Reproducible Smoke Tests:** Automated E2E verification scripts (`scripts/smoke-test.ps1`, `scripts/smoke-test.sh`) verifying actuator health, RBAC security, entity registration, proposal generation, validator pass, version acceptance, optimistic locking 409 conflicts, battery drift, charger outage, and restoration.
- **Comprehensive Benchmarks:** Verified target scale of 300 EVs / 1,500 trips / 40 chargers in **2.38s** (full plan) and **663 ms** (replan), both well within 30s and 2s SLA limits.

---

## 4. Build, Test and Verify

### Compile & Run All 277 Automated Tests:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:Path = "C:\Program Files\Java\jdk-24\bin;" + $env:Path
$env:MAVEN_OPTS = "-Xms32m -Xmx160m -XX:ReservedCodeCacheSize=24m -Xss256k -XX:+UseSerialGC"
.\mvnw.cmd test
```

### Run End-to-End Smoke Test Script:
```powershell
.\scripts\smoke-test.ps1 http://localhost:8080
```

### One-Command Production Docker Deployment:
```bash
docker compose up --build -d
```

---

## 5. Test Suite & Verification Results

The automated test suite achieves **100% pass rate across 277 tests** (0 failures, 0 errors, 0 skipped):

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 277, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 6. Benchmarking & Scalability Summary

| Tier | EVs | Trips | Chargers | Full Plan (p50) | Replan (p50) | Coverage | Independent Validator |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Small | 10 | 50 | 4 | 65 ms | 154 ms | 48.0% | PASS (53 checks) |
| Medium | 50 | 250 | 15 | 1,309 ms | < 1 ms | 62.4% | PASS (317 checks) |
| Large | 100 | 500 | 25 | 2,041 ms | 94 ms | 66.2% | PASS (667 checks) |
| **Target Scale** | **300** | **1,500** | **40** | **2,385 ms** | **663 ms** | **73.7%** | **PASS (2,215 checks)** |

*All 2,215 constraint checks at target scale certified 0 violations.*

---

## 7. Security, REST APIs & Hub Management (Phases 7, 10 & 11)

### Authentication & Roles:
- **ADMIN:** `key-admin-{tenantId}` (Full management permissions).
- **OPERATOR:** `key-operator-{tenantId}` (Planning, replanning, registration, and plan acceptance).
- **VIEWER:** `key-viewer-{tenantId}` (Read-only access to fleet queries, versions, and reports).

### REST API Endpoints:
- `POST /api/v1/vehicles` — Register EV with battery capacity, SoC, efficiency, and available location.
- `GET /api/v1/vehicles` — Query all fleet vehicles for tenant.
- `POST /api/v1/trips` — Ingest commute trip demand with pickup window and latest deadline.
- `GET /api/v1/trips` — List trip demands for tenant.
- `POST /api/v1/hubs` — Register or update charger hub with power rating and tariff.
- `GET /api/v1/hubs` — List charger hubs (supports `?page=0&size=10` pagination).
- `GET /api/v1/hubs/{id}` — Retrieve detailed configuration for single hub.
- `PUT /api/v1/hubs/{id}` — Update hub configuration.
- `GET /api/v1/hubs/{id}/utilization` — Inspect active sessions, available plugs, and utilization rate.
- `POST /api/v1/hubs/{id}/status` — Set operational status (`OPERATIONAL`, `OFFLINE`, `DEGRADED`).
- `GET /api/v1/hubs/recovery-events` — Retrieve recent recovery audit events.
- `GET /api/v1/overview` — Reconciled operational snapshot (vehicles, trips, available/offline chargers).
- `POST /api/v1/plans/propose` — Run optimization and independent validator to return a candidate plan proposal.
- `POST /api/v1/plans/{proposalId}/accept` — Atomically accept proposal with optimistic version checking (`expectedCurrentVersion`) and `Idempotency-Key` deduplication.
- `GET /api/v1/plans/versions` — Query versioned history of accepted fleet plans.
- `POST /api/v1/replanning/battery-drift` — Trigger bounded replanning upon observed SoC telemetry drift under 2.0s SLA.
- `POST /api/v1/replanning/charger-failure` — Trigger rerouting and replanning upon unexpected charger hub outage under 2.0s SLA.
- `POST /api/v1/replanning/charger-restore/{hubId}` — Explicitly restore an offline hub back to operational.

For comprehensive architecture diagrams, reason-code specifications, and mathematical formulations, refer to [DESIGN.md](DESIGN.md).

