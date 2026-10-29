# MoveInSync EV Fleet Charging & Trip Assignment Optimizer
## System Architecture & Technical Design Document

---

## 1. Problem Summary

Modern corporate employee transportation requires balancing zero-emission sustainability goals with stringent service reliability. Electric vehicles (EVs) present distinct operational constraints compared to internal combustion engine (ICE) vehicles:
- **Finite range & battery depletion:** Vehicles must avoid roadside stranding and deep discharge degradation.
- **Non-linear battery charging profiles:** Charging is fast up to 80% State of Charge (SoC), tapering significantly above 80% in the Constant Voltage (CV) phase.
- **Constrained charging infrastructure:** Fixed number of chargers per hub and power delivery ratings.
- **Dynamic electricity pricing:** Peak vs. off-peak tariffs incentivize overnight or shoulder-period charging.
- **Service punctuality:** Employee trips operate under fixed schedules and strict latest allowed finish times.

### Primary Objectives Hierarchy
1. **Never violate hard operational constraints:** 15% battery reserve floor, non-overlapping timelines, punctuality deadlines, and charger hub capacity are inviolable.
2. **Minimize trips requiring backup petrol/diesel cabs:** Service punctuality and full trip coverage are the highest operational priority.
3. **Minimize electricity cost:** Among candidate schedules with identical backup counts, minimize aggregate charging expenditure through tariff-aware scheduling.
4. **Deterministic tie-breaking:** Guarantees repeatable, audit-ready plan output.

---

## 2. Project Requirements Checklist

- [x] **Phase 1: Domain Foundation & Core Calculations** (Status: COMPLETE)
  - [x] Immutable `Location` with Haversine distance, symmetry, spherical curvature, boundary validation.
  - [x] Half-open `TimeWindow` `[start, end)` with non-overlapping adjacent intervals.
  - [x] Timezone-aware electricity `TariffSchedule`, midnight-crossing windows, and DST handling.
  - [x] `EnergyModel` with configurable traffic and AC consumption multipliers.
  - [x] `BatterySafetyCalculator` enforcing strict 15% state-of-charge reserve floor.
  - [x] `TwoPartChargingCurve` modeling fast charging (<80% SoC) and taper (>80% SoC).
  - [x] Invariant-enforcing domain models: `Vehicle`, `Trip`, `ChargerHub`, `ChargingSession`.
  - [x] Comprehensive automated JUnit 5 & AssertJ unit test suite (116 tests passing).
- [x] **Phase 2: Trip Assignment and Charging Optimization Engine** (Status: COMPLETE)
  - [x] Travel-time and road-distance heuristic model (`TravelConfig`, `TravelTimeEstimator`, `DefaultTravelTimeEstimator`).
  - [x] Lazily-populated, memory-bounded road distance cache (`DistanceMatrix`).
  - [x] 5-step trip feasibility evaluation (`TripFeasibilityChecker`, `FeasibilityReason`, `FeasibilityResult`).
  - [x] Vehicle operational timeline tracking with spatial/temporal continuity and 15% reserve check (`VehicleTimeline`, `TimelineEvent`).
  - [x] Hub charger concurrency and oversubscription prevention (`ChargerOccupancyTracker`).
  - [x] Charging opportunity scheduler integrating two-part curves and dynamic tariffs (`ChargingScheduler`).
  - [x] Deterministic greedy construction heuristic with lexicographic objective (`GreedyFleetPlanner`).
  - [x] Bounded local search optimization (`LocalSearchConfig`).
  - [x] Comprehensive plan result and explainability (`FleetPlanResult`, `TripAssignment`).
  - [x] Machine-readable planning quality report with defensible lower bounds (`PlanningQualityReport`).
  - [x] Independent plan validator (`FleetPlanValidator`).
  - [x] Full automated test suite covering all 17 required scenarios (188 total tests passing).
  - [x] Scalability benchmark execution (<150ms for 20 EVs / 50 trips).
- [x] **Independent Plan Validator & Safety Verification Engine** (Status: COMPLETE)
  - [x] Public verification contract `PlanValidator` returning immutable `ValidationReport`.
  - [x] Independent implementation `DefaultPlanValidator` operating with zero trust in planner flags.
  - [x] Battery reserve floor ($\ge 15\%$), capacity ceiling ($\le 100\%$), and energy delta reconciliation.
  - [x] Trip coverage uniqueness, timeline non-overlap, punctuality, deadline compliance, and spatial continuity.
  - [x] $O(N \log N)$ interval sweep-line charger hub concurrency verification with half-open $[start, end)$ semantics.
  - [x] Prohibition of vehicle charging during passenger trips.
  - [x] Tariff cost reconciliation recalculating charging costs from ground-truth schedules.
  - [x] Comprehensive test suite (`PlanValidatorTest`, 19 tests) & benchmark (`PlanValidatorBenchmarkTest`, target scale <1ms for 2,173 checks).
- [ ] **High-Scale Mathematical Optimization & Metaheuristics** (Status: PLANNED)
  - [ ] Support 1,500 trips across 300 EVs within 30-second budget.
- [ ] **Advanced Multi-Hub Charging Scheduler** (Status: PLANNED)
- [ ] **Live Replanning Engine (<2s)** (Status: PLANNED)
- [ ] **Persistence, REST APIs, Observability & Security** (Status: PLANNED)

---

## 3. Phase 2 Architecture & Engine Details

```
+-------------------------------------------------------------------------------------------------+
|                                    OPTIMIZATION ENGINE CORE                                     |
|                                                                                                 |
|   +------------------------------------+             +--------------------------------------+   |
|   |         GreedyFleetPlanner         |             |       TripFeasibilityChecker         |   |
|   |------------------------------------|             |--------------------------------------|   |
|   | - Trip Urgency & Difficulty Sort   |------------>| 1. Vehicle Availability              |   |
|   | - Deterministic Candidate Eval     |             | 2. Pickup Reachability               |   |
|   | - Lexicographic Selection          |             | 3. Trip Deadline Slack               |   |
|   | - Charging Opportunity Injection   |             | 4. Empty Drive Reserve (>15%)        |   |
|   +-----------------+------------------+             | 5. Trip Energy Reserve (>15%)        |   |
|                     |                                +--------------------------------------+   |
|                     v                                                   ^                       |
|   +------------------------------------+                                |                       |
|   |          ChargingScheduler         |                                |                       |
|   |------------------------------------|                                |                       |
|   | - Repositioning to Hub             |                                |                       |
|   | - TwoPartChargingCurve Duration    |--------------------------------+                       |
|   | - Hub Concurrency Check            |                                                        |
|   | - TariffSchedule Cost Integration  |                                                        |
|   +-----------------+------------------+                                                        |
|                     |                                                                           |
|                     v                                                                           |
|   +------------------------------------+             +--------------------------------------+   |
|   |      ChargerOccupancyTracker       |             |           VehicleTimeline            |   |
|   |------------------------------------|             |--------------------------------------|   |
|   | - Active Session Intervals [t1, t2)|             | - Non-Overlapping Events             |   |
|   | - Max Concurrency <= chargerCount  |             | - Spatial Continuity                 |   |
|   +------------------------------------+             | - Continuous SoC Floor (>= 0.15)     |   |
|                                                      +--------------------------------------+   |
|                                                                         |                       |
|                                                                         v                       |
|   +------------------------------------+             +--------------------------------------+   |
|   |         FleetPlanValidator         |             |           FleetPlanResult            |   |
|   |------------------------------------|             |--------------------------------------|   |
|   | - Independent Constraint Replay    |<------------| - Assignments & Backup Reasons       |   |
|   | - 0 Violation Certification        |             | - Vehicle Timelines & Charging Log   |   |
|   +------------------------------------+             | - PlanningQualityReport & Metrics    |   |
|                                                      +--------------------------------------+   |
+-------------------------------------------------------------------------------------------------+
```

---

## 5. Independent Plan Validator Architecture & Verification Algorithms

The UrjaRoute validator (`com.moveinsync.evfleet.validator`) acts as an **independent, adversarial safety gate**. It does not trust any internal planner flags (`feasible=true`), cached distances, or precomputed scores. It re-evaluates the entire operational plan from ground truth.

```
                           +------------------------+
                           |     FleetPlanResult    |
                           +-----------+------------+
                                       |
                                       v
                     +------------------------------------+
                     |        DefaultPlanValidator        |
                     +-----------------+------------------+
                                       |
     +-----------------+---------------+---------------+-----------------+
     |                 |                               |                 |
     v                 v                               v                 v
[Pillar 1: Battery] [Pillar 2: Trips & Timing]     [Pillar 3: Chargers] [Pillar 4: Tariffs]
- SoC >= 15% floor  - 100% trip accounting (unique)- O(N log N) sweep-  - Recalculates cost
- SoC <= 100% cap   - Timeline event non-overlap    line concurrency      from tariff slots
- Energy delta      - Punctual pickup & deadline    - Half-open [s, e)  - Reconciles total
  reconciliation    - Spatial continuity            - No charge on trip   plan expenditure
     |                 |                               |                 |
     +-----------------+---------------+---------------+-----------------+
                                       |
                                       v
                           +------------------------+
                           |    ValidationReport    |
                           |  - VALID / INVALID     |
                           |  - List<PlanViolation> |
                           +------------------------+
```

### Four Verification Pillars

1. **Battery Reserve & SoC Invariants:**
   - Evaluates vehicle initial SoC from input specification.
   - At every single timeline event (empty movement, passenger trip, charging session), checks $\text{SoC} \ge 0.15$ and $\text{SoC} \le 1.00$.
   - Reconciles energy deltas with battery capacity:
     $$|\text{SoC}_{\text{end}} - (\text{SoC}_{\text{start}} \pm \frac{\Delta E}{\text{capacity}})| \le 10^{-4}$$
   - Energy delta must be negative for movement events and positive for charging events.

2. **Trip Coverage & Temporal Continuity:**
   - Every requested trip in `PlanningInput` must appear in the plan's assignments exactly once.
   - Assigned trips must be mapped to valid vehicles; unassigned trips must have valid backup reasons.
   - Events within each vehicle timeline must be chronologically ordered and strictly non-overlapping:
     $$\text{event}_{i}.\text{endTime} \le \text{event}_{i+1}.\text{startTime}$$
   - Passenger trips must start $\ge \text{trip}.\text{scheduledStartTime}$ and end $\le \text{trip}.\text{latestAllowedFinishTime}$.
   - Spatial continuity: $\text{event}_{i}.\text{endLocation} = \text{event}_{i+1}.\text{startLocation}$.

3. **Charger Hub Concurrency via Interval Sweep-Line:**
   - Evaluates all charging sessions at each hub using an event-driven sweep-line algorithm:
     - Each session $[t_{\text{start}}, t_{\text{end}})$ generates a `START (+1)` and `END (-1)` event.
     - Events are sorted by timestamp. **To enforce half-open $[t_{\text{start}}, t_{\text{end}})$ semantics, `END` events are processed before `START` events at identical timestamps.**
     - Running active session count is verified at every event: $\text{activeSessions}(t) \le N_{\text{chargers}}(\text{Hub})$.
   - Prohibits vehicle charging while concurrently engaged in passenger transit.

4. **Tariff Cost Reconciliation:**
   - For every charging session, independently determines overlapping tariff slots from `TariffSchedule`.
   - Computes expected energy cost $\sum \Delta E_{\text{slot}} \times \text{rate}_{\text{slot}}$ and verifies match against reported session cost.
   - Reconciles total plan charging cost with sum of session costs within a $0.05$ margin.

---

## 6. Violation Code Taxonomy

When an operational constraint is violated, the validator records an immutable `PlanViolation` with an exact `ViolationCode`:

| Violation Code | Category | Operational Severity | Description |
| :--- | :--- | :--- | :--- |
| `BATTERY_RESERVE_VIOLATION` | Safety | Critical Hard Violation | Vehicle battery SoC dropped below the mandatory 15% reserve floor. |
| `BATTERY_CAPACITY_EXCEEDED` | Safety | Hard Violation | Battery charged beyond 100% rated capacity. |
| `ENERGY_ACCOUNTING_MISMATCH`| Physics | Invariant Breach | Event energy delta does not reconcile with starting and ending SoC. |
| `TRIP_UNASSIGNED` | Service | Service Degradation | Requested trip omitted from plan without designated backup cab. |
| `DUPLICATE_TRIP_ASSIGNMENT` | Service | Dispatch Inconsistency | A single trip is assigned to multiple vehicles or both EV and backup. |
| `PICKUP_TIME_INFEASIBLE` | Punctuality | Service Violation | Vehicle arrives at pickup location after scheduled start time. |
| `TRIP_DEADLINE_VIOLATION` | Punctuality | Service Violation | Passenger dropoff occurs after latest allowed finish time. |
| `VEHICLE_TIMELINE_OVERLAP` | Operations | Physical Impossibility | Vehicle has overlapping events scheduled in its operational timeline. |
| `SPATIAL_DISCONTINUITY` | Physics | Physical Impossibility | Next event start location does not match prior event end location. |
| `CHARGER_CAPACITY_EXCEEDED` | Infrastructure | Physical Overload | Concurrent active charging sessions exceed hub charger count. |
| `CHARGING_DURING_TRIP` | Safety | Severe Policy Breach | Vehicle is recorded charging while carrying commute passengers. |
| `CHARGING_SESSION_INVALID` | Operations | Operational Fault | Charging session start $\ge$ end time or negative energy transfer. |
| `CHARGING_COST_MISMATCH` | Financial | Accounting Mismatch | Reported session or plan cost diverges from ground-truth tariff rate. |
| `METRIC_INCONSISTENCY` | Integrity | Reporting Discrepancy | Quality report metrics disagree with underlying plan data. |
| `UNKNOWN_ENTITY_REFERENCED`| Referential | Integrity Breach | Plan references a vehicle, trip, or hub not present in input. |
| `MISSING_VALIDATION_DATA` | Validation | Indeterminate | Missing required input datasets to perform verification. |

---

## 7. Reason Code Taxonomy (Planner Diagnostics)

When an EV cannot serve a trip, the planner attaches an explicit, machine-readable `FeasibilityReason` to explain the assignment decision:

| Reason Code | Category | Operational Meaning |
| :--- | :--- | :--- |
| `NONE` | Success | Trip is feasible for the vehicle. |
| `VEHICLE_UNAVAILABLE` | Availability | Vehicle is in maintenance, on an active trip, or already busy during the shift. |
| `PICKUP_UNREACHABLE` | Timing | Vehicle cannot drive from its current location to pickup before `scheduledStartTime`. |
| `DEADLINE_INFEASIBLE` | Timing | Trip travel duration exceeds the window between start and `latestAllowedFinishTime`. |
| `INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE` | Energy | Repositioning deadhead to pickup drops battery SoC below 15% reserve floor. |
| `INSUFFICIENT_ENERGY_FOR_TRIP` | Energy | Empty travel + trip journey depletes battery SoC below 15% reserve floor. |
| `CHARGER_CAPACITY_UNAVAILABLE` | Infrastructure | All physical chargers at the target hub are occupied during the required charging window. |
| `INVALID_INPUT` | Validation | Input vehicle or trip entity is null or violates domain constraints. |

---

## 8. Mathematical Formulations & Heuristic Logic

### A. Road Distance & Travel Time Estimation
- **Road Distance Approximation:**
  $$d_{\text{road}}(A, B) = f_{\text{road}} \times d_{\text{Haversine}}(A, B)$$
  where default $f_{\text{road}} = 1.3$ (configurable via `TravelConfig`).
- **Travel Time:**
  $$t_{\text{travel}}(A, B) = \frac{d_{\text{road}}(A, B)}{v_{\text{avg}}}$$
  where default $v_{\text{avg}} = 25.0\text{ km/h}$ ($6.94\text{ m/s}$).

### B. Energy Consumption & Battery Reserve Invariant
- **Energy Required:**
  $$E_{\text{req}} = d \times C_{\text{base}} \times M_{\text{traffic}} \times M_{\text{ac}}$$
- **Invariant Reserve Floor:**
  $$\text{SoC}_{\text{event\_end}} = \text{SoC}_{\text{event\_start}} - \frac{E_{\text{req}}}{B} \ge 0.15$$

### C. Piecewise Two-Part Charging Dynamics
$$\Delta t_{\text{charge}} = \frac{E_{<80\%}}{P_{\text{charger}} \times 1.0} + \frac{E_{>80\%}}{P_{\text{charger}} \times 0.50}$$

### D. Hub Concurrency Invariant (Sweep-Line Formulation)
For any hub $H$ and any instant $t$:
$$\sum_{s \in \text{Sessions}(H)} \mathbb{I}(s_{\text{start}} \le t < s_{\text{end}}) \le N_{\text{chargers}}(H)$$

---

## 9. Time and Space Complexity Analysis

- **Validator Complexity:**
  - Battery & Timeline Verification: $O(V \cdot E)$ where $V$ is vehicles and $E$ is events per timeline ($O(N)$ linear).
  - Charger Hub Concurrency: $O(S \log S)$ where $S$ is charging sessions, via sweep-line sort.
  - Overall Validator Runtime: $O(T + V \cdot E + S \log S)$, running in **< 1 ms** across 100 vehicles / 500 trips.
- **Planner Complexity:**
  - Trip Sorting: $O(T \log T)$ where $T$ is the number of trips.
  - Candidate Evaluation: $O(T \cdot (V + H \cdot K \log K))$.
- **Distance Matrix Space:** $O(L^2)$ where $L$ is distinct locations ($< 4\text{ MB}$ memory).

---

## 10. Requirement Traceability Matrix

| Requirement | Implementation Component | Test / Verification Evidence | Status |
| :--- | :--- | :--- | :--- |
| **Travel Config & Estimator** | `TravelConfig`, `TravelTimeEstimator` | `TravelTests` (35 tests) | **COMPLETE** |
| **Distance Matrix Cache** | `DistanceMatrix` | `TravelTests$DistanceMatrixTests` (11 tests) | **COMPLETE** |
| **Trip Feasibility Checker** | `TripFeasibilityChecker` | `TripFeasibilityCheckerTest` (20 tests) | **COMPLETE** |
| **Vehicle Timeline Simulation** | `VehicleTimeline`, `TimelineEvent` | `GreedyFleetPlannerTest#timelineConsistencyMaintained` | **COMPLETE** |
| **Hub Concurrency Protection** | `ChargerOccupancyTracker` | `GreedyFleetPlannerTest#hubCapacityPreventsConcurrencyExceeded` | **COMPLETE** |
| **Charging Scheduler** | `ChargingScheduler` | `GreedyFleetPlannerTest#chargingScheduledWhenSocLow` | **COMPLETE** |
| **Greedy Fleet Planner** | `GreedyFleetPlanner` | `GreedyFleetPlannerTest` (17 tests) | **COMPLETE** |
| **Independent Plan Validator**| `PlanValidator`, `DefaultPlanValidator` | `PlanValidatorTest` (19 tests) | **COMPLETE** |
| **15% Reserve Floor Audit** | `DefaultPlanValidator` | `PlanValidatorTest$BatteryVerificationTests` | **COMPLETE** |
| **Charger Sweep-Line Audit** | `DefaultPlanValidator` | `PlanValidatorTest$ChargerConcurrencyTests` | **COMPLETE** |
| **Trip Integrity Audit** | `DefaultPlanValidator` | `PlanValidatorTest$TripAssignmentTests` | **COMPLETE** |
| **Tariff Reconciliation** | `DefaultPlanValidator` | `PlanValidatorTest#tariffCostReconciliation` | **COMPLETE** |
| **Validator Scalability** | `PlanValidatorBenchmarkTest` | Target Scale 100 EVs/500 trips < 1 ms | **COMPLETE** |
| **Deterministic Planning** | `GreedyFleetPlanner` | `GreedyFleetPlannerTest#deterministicOutput` | **COMPLETE** |
| **Plan Explainability Service** | `PlanExplanationService` | `PlanExplanationServiceTest` (7 tests) | **COMPLETE** |
| **Trip Assignment Audit Trail** | `AssignmentExplanation`, `AlternativeEvaluation` | `PlanExplanationServiceTest$BasicExplanationTests` | **COMPLETE** |
| **Transparent Cost Breakdown** | `CostBreakdown` | `PlanExplanationServiceTest$CostBreakdownTests` | **COMPLETE** |
| **Plan Quality & Health Metrics** | `PlanQualityReport` | `PlanExplanationServiceTest$PlanQualityReportTests` | **COMPLETE** |
| **Sound Lower-Bound Analysis** | `LowerBoundResult`, `LowerBoundStatus` | `PlanExplanationServiceTest$LowerBoundAnalysisTests` | **COMPLETE** |
| **Live Battery Drift Replanning** | `ReplanningService`, `BatteryDriftEvent` | `ReplanningServiceTest$BatteryDriftTests` | **COMPLETE** |
| **Charger Failure Recovery** | `ReplanningService`, `ChargerFailureEvent` | `ReplanningServiceTest$ChargerFailureTests` | **COMPLETE** |
| **Replanning Latency SLA (<2s)** | `ReplanningService` | `ReplanningBenchmarkTest` (80ms / 449ms) | **COMPLETE** |
| **PostgreSQL 16 Schema & Flyway** | `V1__initial_schema.sql`, `docker-compose.yml` | Flyway Migration on PostgreSQL 16 | **COMPLETE** |
| **Multi-Tenant Fleet REST APIs** | `FleetController`, `TripController` | `PlanningIntegrationTest#testEndToEndPlanningFlow` | **COMPLETE** |
| **Atomic Plan Acceptance & CAS** | `PlanningController`, `FleetPersistenceService` | `PlanningIntegrationTest#testVersionConflictReturns409` | **COMPLETE** |
| **Idempotency Deduplication** | `IdempotencyRecordRepository` | `PlanningIntegrationTest#testIdempotentAcceptance` | **COMPLETE** |

---

## 11. Phase 6 Architecture: Multi-Tenant REST APIs & Optimistic Plan Versioning

### A. Persistence & Multi-Tenancy
- **Tenant Isolation:** All entities (`TenantEntity`, `VehicleEntity`, `TripEntity`, `ChargerHubEntity`, `PlanProposalEntity`, `PlanVersionEntity`, `TripAssignmentEntity`, `ChargingSessionEntity`, `AuditRecordEntity`, `IdempotencyRecordEntity`) carry strict `tenant_id` foreign keys and indexed constraints.
- **Flyway Migrations:** `V1__initial_schema.sql` establishes production-ready relational schemas with referential integrity and cascading deletions.
- **Docker Compose:** Fully reproducible PostgreSQL 16 Alpine container with readiness health check (`pg_isready`).

### B. Optimistic Locking & Compare-and-Set (CAS) Acceptance
- Every candidate plan begins as a `PlanProposal`.
- Acceptance endpoint `POST /api/v1/plans/{proposalId}/accept` requires an `expectedCurrentVersion` field.
- If the current active version in the database diverges from `expectedCurrentVersion`, an RFC 7807 `409 Conflict` `ProblemDetail` is returned, preventing concurrent overwrite hazards.
- Accepted proposals atomically transition to `PlanVersionEntity` with status `ACCEPTED`, marking the former version as `SUPERSEDED`.

### C. Idempotency Key De-duplication
- Mutation requests support the `Idempotency-Key` HTTP header.
- Repeated submissions return the exact original JSON response and HTTP status code from `IdempotencyRecordEntity` without repeating optimization or database mutations.

---

## 12. Known Limitations & Roadmap

1. **Greedy Horizon:** The greedy heuristic assigns trips sequentially ordered by urgency. Complex multi-trip chaining across dense shifts will be further optimized using metaheuristics / ALNS (Adaptive Large Neighborhood Search).
2. **Dynamic Routing Graphs:** The travel-time estimator currently uses an urban road factor model ($1.3 \times$ Haversine). Future extensions can plug in live OpenStreetMap / OSRM matrix providers via `TravelTimeEstimator`.
3. **Distributed Lock Coordination:** Current optimistic version checking provides atomic consistency at the tenant level. Future clustering can integrate distributed Redis / database advisory locks if sub-millisecond contention peaks occur.
