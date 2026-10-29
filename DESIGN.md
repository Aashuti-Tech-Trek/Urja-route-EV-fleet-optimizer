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
- [ ] **Phase 3: High-Scale Trip Assignment & Mathematical Optimization** (Status: PLANNED)
  - [ ] Support 1,500 trips across 300 EVs within 30-second budget.
- [ ] **Phase 4: Advanced Multi-Hub Charging Scheduler** (Status: PLANNED)
- [ ] **Phase 5: Production Plan Replay & Audit Validator** (Status: PLANNED)
- [ ] **Phase 6: Live Replanning Engine (<2s)** (Status: PLANNED)
- [ ] **Phase 7: Persistence, REST APIs, Observability & Security** (Status: PLANNED)

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

## 4. Reason Code Taxonomy

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

## 5. Mathematical Formulations & Heuristic Logic

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

### D. Hub Concurrency Invariant
For any hub $H$ and any instant $t$:
$$\sum_{s \in \text{Sessions}(H)} \mathbb{I}(s_{\text{start}} \le t < s_{\text{end}}) \le N_{\text{chargers}}(H)$$

---

## 6. Time and Space Complexity Analysis

- **Trip Sorting:** $O(T \log T)$ where $T$ is the number of trips.
- **Candidate Evaluation:** For each trip, evaluating $V$ vehicles against $H$ hubs:
  - Direct feasibility check: $O(1)$ with cached distance lookup.
  - Charging schedule evaluation: $O(H \times K \log K)$ where $K$ is the number of overlapping sessions at the hub.
  - Overall greedy construction: $O(T \cdot (V + H \cdot K \log K))$.
- **Distance Matrix Space:** $O(L^2)$ where $L$ is the number of distinct locations in a planning run. For 300 vehicles + 40 hubs, $L \approx 340$, requiring $< 4\text{ MB}$ memory.
- **Observed Benchmark:** 20 EVs and 50 trips planned and validated in **143 milliseconds**.

---

## 7. Requirement Traceability Matrix

| Requirement | Implementation Component | Test / Verification Evidence | Status |
| :--- | :--- | :--- | :--- |
| **Travel Config & Estimator** | `TravelConfig`, `TravelTimeEstimator` | `TravelTests` (35 tests) | **COMPLETE** |
| **Distance Matrix Cache** | `DistanceMatrix` | `TravelTests$DistanceMatrixTests` (11 tests) | **COMPLETE** |
| **Trip Feasibility Checker** | `TripFeasibilityChecker` | `TripFeasibilityCheckerTest` (20 tests) | **COMPLETE** |
| **Vehicle Timeline Simulation** | `VehicleTimeline`, `TimelineEvent` | `GreedyFleetPlannerTest#timelineConsistencyMaintained` | **COMPLETE** |
| **Hub Concurrency Protection** | `ChargerOccupancyTracker` | `GreedyFleetPlannerTest#hubCapacityPreventsConcurrencyExceeded` | **COMPLETE** |
| **Charging Scheduler** | `ChargingScheduler` | `GreedyFleetPlannerTest#chargingScheduledWhenSocLow` | **COMPLETE** |
| **Greedy Fleet Planner** | `GreedyFleetPlanner` | `GreedyFleetPlannerTest` (17 tests) | **COMPLETE** |
| **Backup Prioritization** | `GreedyFleetPlanner` | `GreedyFleetPlannerTest#backupMinimizationPrioritizedOverCost` | **COMPLETE** |
| **Deterministic Planning** | `GreedyFleetPlanner` | `GreedyFleetPlannerTest#deterministicOutput` | **COMPLETE** |
| **Independent Plan Validator**| `FleetPlanValidator` | `FleetPlanValidator` verified clean across all runs | **COMPLETE** |
| **Scalability Benchmark** | `GreedyFleetPlannerTest` | < 150 ms execution verified | **COMPLETE** |

---

## 8. Known Limitations & Phase 3 Roadmap

1. **Greedy Horizon:** The greedy heuristic assigns trips sequentially ordered by urgency. Complex multi-trip chaining across dense shifts will be optimized in Phase 3 using metaheuristics / ALNS (Adaptive Large Neighborhood Search).
2. **Fixed Speed Assumption:** The current travel-time estimator applies a constant urban speed model. Integration with dynamic routing graphs or external road routing services is planned for later phases.
3. **Local Search Scope:** The current local search is bounded and focused on candidate preservation. Phase 3 will introduce comprehensive trip-swap and charging-reinsertion operators.
