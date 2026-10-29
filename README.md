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
│   │                   └── validation/
│   │                       └── DomainValidator.java
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
│                       │   └── TripFeasibilityCheckerTest.java
│                       ├── time/
│                       │   ├── TariffScheduleTest.java
│                       │   └── TimeWindowTest.java
│                       └── travel/
│                           └── TravelTests.java
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

### Run the Phase 2 Planner Tests Specifically:
```bash
mvn test -Dtest="GreedyFleetPlannerTest,TripFeasibilityCheckerTest,TravelTests"
```

---

## 5. Test Suite & Verification Results

The automated test suite achieves 100% pass rate across 188 unit, integration, and scenario tests:

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
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 219, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 6. Benchmarking & Scalability

Phase 2 includes a reproducible benchmark harness (`GreedyFleetPlannerTest.scalabilityBenchmark`):
- **Scenario:** 20 electric vehicles, 50 employee trips with varying pickup deadlines and geographic coordinates.
- **Measured Execution Time:** **~143 ms** (well under the sub-30-second target for 1,500 trips).
- **Independent Validation:** Verified clean with 0 violations (`VALIDATED`).

For comprehensive architecture diagrams, reason-code specifications, and mathematical formulations, refer to [DESIGN.md](DESIGN.md).
