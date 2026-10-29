# MoveInSync EV Fleet Charging & Trip Assignment Optimizer

> **MoveInSync Backend Case Study 2026 — Advanced Track (Case Study 6)**
> Enterprise-grade EV fleet optimizer for corporate commute operations.

---

## 1. Project Overview

The **MoveInSync EV Fleet Optimizer** is an algorithmic planning and execution system designed to optimize electric cab assignments and charging schedules for high-throughput corporate commutes.

### Eventual Scale Target:
- **300 Electric Vehicles** across corporate fleet operations.
- **40 Chargers** distributed across strategic charging hubs.
- **1,500 Employee Trips** in a single evening shift window.
- **Full Planning Target:** Under 30 seconds for 1,500 trips.
- **Live Replanning Target:** Under 2 seconds upon telemetry battery drift or charger failure.

### Core Objectives Priority:
1. **Minimize trips requiring backup petrol or diesel cabs** (Uncompromised service reliability).
2. **Minimize electricity cost** among candidate plans with identical backup trips.

---

## 2. Implemented in Phase 1

Phase 1 provides the mathematical foundations, core calculations, and domain model:

1. **Geographic Domain (`location`):**
   - Immutable `Location` value object.
   - Great-circle Haversine spherical distance calculation (in kilometres).
   - Coordinate bounds validation ($[-90, 90]$ latitude, $[-180, 180]$ longitude, rejection of NaN/Infinity).
2. **Time and Tariffs (`time`):**
   - `TimeWindow` with half-open $[start, end)$ interval semantics and non-overlapping adjacent boundaries.
   - `TariffSchedule`, `TariffSlot`, `TariffRate` supporting midnight-crossing windows (e.g., 22:00 to 06:00), local timezones (`ZoneId`), and Daylight Saving Time (DST) transitions.
   - Proportional multi-slot electricity cost calculation using `BigDecimal`.
3. **Energy Model (`energy`):**
   - `EnergyModel` interface and `StandardEnergyModel`.
   - Configurable traffic multiplier and air conditioning (AC) multiplier.
   - Reusable `BatterySafetyCalculator` enforcing a strict **15% state-of-charge reserve floor** ($0.15 \times B$).
   - Max travel distance calculation and conservative floating-point safety.
4. **Charging Dynamics (`charging`):**
   - `ChargingCurve` interface and `TwoPartChargingCurve`.
   - Accurate two-part CC-CV piecewise linear curve: fast charging below 80% SoC, tapered charging above 80% SoC.
   - Resulting SoC inversion logic and time/duration conversions.
5. **Domain Aggregate Core (`domain`):**
   - `Vehicle`: Architectural separation of immutable physical specs (`VehicleSpecification`) and mutable operational state (`VehicleState` and `VehicleAvailability`).
   - `Trip`: Start/dropoff locations, distance, scheduled start time, and latest allowed finish time.
   - `ChargerHub`: Hub location, charger count ($N$), individual charger power rating ($P_{\text{kW}}$), and hub tariff schedule.
   - `ChargingSession`: Start/end timestamps, start/end SoC, energy delivered, estimated cost, and battery capacity validation.

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
│   │                   ├── time/
│   │                   │   ├── TariffRate.java
│   │                   │   ├── TariffSchedule.java
│   │                   │   ├── TariffSlot.java
│   │                   │   ├── TariffType.java
│   │                   │   └── TimeWindow.java
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
│                       └── time/
│                           ├── TariffScheduleTest.java
│                           └── TimeWindowTest.java
├── DESIGN.md
├── pom.xml
└── README.md
```

---

## 4. Prerequisites

- **Java Development Kit (JDK):** Java 21 or higher (LTS).
- **Build Tool:** Apache Maven 3.9+ or the included Maven wrapper (`./mvnw` / `mvnw.cmd`).
- **Operating System:** Windows, macOS, or Linux.

---

## 5. Build and Test Instructions

### Compile the Project:
```bash
mvn clean compile
```

### Run All Automated Unit & Integration Tests:
```bash
mvn test
```

### Package into Executable JAR:
```bash
mvn clean package
```

---

## 6. Test Suite & Verification Results

The Phase 1 automated test suite achieves comprehensive coverage across edge cases, invariants, and calculations:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.moveinsync.evfleet.charging.TwoPartChargingCurveTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
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
[INFO] Running com.moveinsync.evfleet.location.LocationTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.time.TariffScheduleTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.time.TimeWindowTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moveinsync.evfleet.EvFleetApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 116, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

For detailed architectural diagrams, design decisions, and requirements traceability, refer to [DESIGN.md](DESIGN.md).
