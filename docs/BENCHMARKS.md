# UrjaRoute Performance & Benchmark Report (Phase 8)

## 1. Overview
This document details the reproducible 4-tier benchmark harness executed for **UrjaRoute (EV Fleet Charging & Trip Assignment Optimizer)**, measuring optimization scalability, execution latencies, live disruption replanning response times, and constraint verification under independent validation.

## 2. Test Environment & Hardware Specification
- **Operating System**: Windows 11 Enterprise (64-bit)
- **Java Virtual Machine**: OpenJDK 64-Bit Server VM (build 24+36-3660, release 24)
- **Java Compilation Target**: Java 21 / Spring Boot 3.3.5
- **Build & Test Automation**: Apache Maven 3.9.9 (`.\mvnw.cmd`)
- **JVM Execution Flags**:
  `-Xms64m -Xmx384m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC`
- **Benchmark Suite Class**: `com.moveinsync.evfleet.planner.PlanningBenchmarkSuiteTest`

## 3. Four-Tier Benchmark Results

| Tier | EVs | Trips | Hubs / Chargers | Warmup (ms) | Full Plan p50 (ms) | Full Plan p95 (ms) | Full Plan p99 (ms) | Live Replan SLA (ms) | EV Assigned Trips | Backup Cab Trips | EV Service Coverage | Total Charging Cost | Independent Validator |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Small** | 10 | 50 | 2 hubs / 4 chargers | 362 ms | **65 ms** | 103 ms | 103 ms | **154 ms** | 24 | 26 | 48.0% | ₹ 0.00 | **PASS** (53 checks) |
| **Medium** | 50 | 250 | 5 hubs / 15 chargers | 1,561 ms | **1,309 ms** | 1,628 ms | 1,628 ms | **< 1 ms** | 156 | 94 | 62.4% | ₹ 0.00 | **PASS** (317 checks) |
| **Large** | 100 | 500 | 8 hubs / 25 chargers | 2,080 ms | **2,041 ms** | 2,095 ms | 2,095 ms | **94 ms** | 331 | 169 | 66.2% | ₹ 0.00 | **PASS** (667 checks) |
| **Target** | **300** | **1,500** | **10 hubs / 40 chargers** | 2,277 ms | **2,385 ms** | **2,943 ms** | **2,943 ms** | **663 ms** | **1,105** | **395** | **73.7%** | ₹ 0.00 | **PASS** (**2,215 checks**) |

## 4. SLA Verification & Hard Performance Target Analysis
- **Full Planning Target SLA (< 30.00 seconds)**:
  - **Target Scale Actual (p50)**: **2.385 seconds** (12.5x faster than requirement!).
  - **Target Scale Actual (p95 / p99)**: **2.943 seconds** (10x faster than requirement!).
- **Live Disruption Replanning Target SLA (< 2.00 seconds)**:
  - **Target Scale Actual**: **663 milliseconds** (3x faster than the 2-second ceiling!).
- **Constraint Integrity & Verification**:
  - Independent validator `com.moveinsync.evfleet.validator.DefaultPlanValidator` confirmed **100% PASS** across all 2,215 physical, temporal, spatial, and occupancy checks.
  - Zero 15% SoC reserve floor violations.
  - Zero charger concurrency / oversubscription violations across all hubs.
  - Deterministic tie-breaking and lexicographic optimization ordering preserved.

## 5. Reproducing the Benchmark
To run the automated benchmark harness and generate updated CSV metrics in `target/benchmark-results.csv`:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:Path = "C:\Program Files\Java\jdk-24\bin;" + $env:Path
$env:MAVEN_OPTS = "-Xms64m -Xmx384m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC"
.\mvnw.cmd test -Dtest=PlanningBenchmarkSuiteTest
```
