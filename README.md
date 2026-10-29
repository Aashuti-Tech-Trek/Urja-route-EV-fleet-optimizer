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

### Run Plan Validator Tests Specifically:
```bash
mvn test -Dtest="PlanValidatorTest,PlanValidatorBenchmarkTest"
```

---

## 5. Test Suite & Verification Results

The automated test suite achieves 100% pass rate across 241 unit, integration, validation, and benchmark tests:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.moveinsync.evfleet.domain.ChargerHubTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.domain.ChargingSessionTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.domain.TripTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.domain.VehicleTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.energy.BatterySafetyCalculatorTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.energy.StandardEnergyModelTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.EvFleetApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.location.LocationTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.planner.GreedyFleetPlannerTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.planner.Phase2QualityGateAuditTest
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.planner.TripFeasibilityCheckerTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.time.TariffScheduleTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.time.TimeWindowTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.travel.TravelTests
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.validator.PlanValidatorBenchmarkTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.validator.PlanValidatorTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 241, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 6. Benchmarking & Scalability

The validator performance harness (`PlanValidatorBenchmarkTest`) guarantees sub-second validation throughput:
- **Small Scale:** 10 vehicles, 25 trips, 3 hubs $\rightarrow$ **< 1 ms** (55 checks).
- **Medium Scale:** 30 vehicles, 100 trips, 5 hubs $\rightarrow$ **< 1 ms** (311 checks).
- **Target Scale:** 100 vehicles, 500 trips, 20 hubs $\rightarrow$ **< 1 ms** (2,173 checks, certified 0 violations).

For comprehensive architecture diagrams, reason-code specifications, and mathematical formulations, refer to [DESIGN.md](DESIGN.md).
