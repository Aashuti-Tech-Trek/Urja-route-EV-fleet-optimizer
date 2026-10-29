package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.replan.BatteryDriftEvent;
import com.moveinsync.evfleet.replan.ReplanningResult;
import com.moveinsync.evfleet.replan.ReplanningService;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.validator.DefaultPlanValidator;
import com.moveinsync.evfleet.validator.ValidationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 8: Comprehensive Planning & Replanning Benchmark Harness")
public class PlanningBenchmarkSuiteTest {

    private final Instant baseTime = Instant.parse("2026-10-29T08:00:00Z");

    public record BenchmarkResult(
        String scenarioName,
        int evCount,
        int tripCount,
        int chargerCount,
        long warmUpPlanDurationMs,
        long planDurationP50Ms,
        long planDurationP95Ms,
        long planDurationP99Ms,
        long replanDurationP50Ms,
        int evAssignedCount,
        int backupTripCount,
        double serviceCoveragePct,
        double totalCostInr,
        boolean validationPassed,
        int totalValidationChecks
    ) {}

    private PlanningInput generateScenario(String runId, int numVehicles, int numHubs, int totalChargers, int numTrips, long seed) {
        Random rand = new Random(seed);

        TariffRate standardRate = TariffRate.of(8.50, "INR");
        TariffSchedule schedule = new TariffSchedule(ZoneId.of("Asia/Kolkata"), "INR", List.of(), standardRate);

        List<ChargerHub> hubs = new ArrayList<>(numHubs);
        int chargersPerHub = Math.max(1, totalChargers / numHubs);
        for (int h = 0; h < numHubs; h++) {
            Location loc = new Location(12.9000 + rand.nextDouble() * 0.15, 77.5500 + rand.nextDouble() * 0.15);
            hubs.add(new ChargerHub("HUB-" + (h + 1), "Hub " + (h + 1), loc, chargersPerHub, 60.0, schedule));
        }

        List<Vehicle> vehicles = new ArrayList<>(numVehicles);
        for (int v = 0; v < numVehicles; v++) {
            Location loc = hubs.get(v % hubs.size()).getLocation();
            double soc = 0.60 + rand.nextDouble() * 0.35;
            vehicles.add(Vehicle.create("EV-" + (v + 1), "TENANT-BENCH", 60.0, 0.18, soc, loc, baseTime));
        }

        List<Trip> trips = new ArrayList<>(numTrips);
        for (int t = 0; t < numTrips; t++) {
            Location p = new Location(12.9200 + rand.nextDouble() * 0.12, 77.5600 + rand.nextDouble() * 0.12);
            Location d = new Location(12.9200 + rand.nextDouble() * 0.12, 77.5600 + rand.nextDouble() * 0.12);
            double dist = 6.0 + rand.nextDouble() * 14.0;
            Instant start = baseTime.plusSeconds((t % 25) * 240L + rand.nextInt(120));
            Instant deadline = start.plusSeconds(3600 + (long) (dist * 120));
            trips.add(new Trip("TRIP-" + (t + 1), "TENANT-BENCH", p, d, dist, start, deadline));
        }

        return new PlanningInput(runId, vehicles, trips, hubs);
    }

    private BenchmarkResult runScenarioBenchmark(String name, int evs, int trips, int hubs, int chargers, int iterations) {
        GreedyFleetPlanner planner = GreedyFleetPlanner.createDefault();
        DefaultPlanValidator validator = new DefaultPlanValidator();
        ReplanningService replanningService = new ReplanningService(
            GreedyFleetPlanner.createFastReplanner(), validator, Duration.ofSeconds(2)
        );

        // 1. Warm-up run (excluded from percentiles)
        PlanningInput warmUpInput = generateScenario("WARMUP-" + name, evs, hubs, chargers, trips, 1337L);
        long warmUpStart = System.currentTimeMillis();
        FleetPlanResult warmUpPlan = planner.plan(warmUpInput);
        long warmUpDuration = System.currentTimeMillis() - warmUpStart;

        // 2. Measured planning runs across iterations
        List<Long> planTimes = new ArrayList<>(iterations);
        FleetPlanResult latestPlan = null;
        PlanningInput measuredInput = null;

        for (int i = 0; i < iterations; i++) {
            measuredInput = generateScenario("BENCH-" + name + "-" + i, evs, hubs, chargers, trips, 42L + i);
            long start = System.currentTimeMillis();
            latestPlan = planner.plan(measuredInput);
            planTimes.add(System.currentTimeMillis() - start);
        }

        Collections.sort(planTimes);
        long p50 = planTimes.get((int) (planTimes.size() * 0.50));
        long p95 = planTimes.get((int) (planTimes.size() * 0.95));
        long p99 = planTimes.get((int) (planTimes.size() * 0.99));

        // 3. Measured replanning run
        BatteryDriftEvent drift = new BatteryDriftEvent(
            "DRIFT-" + name, measuredInput.getVehicles().get(0).getVehicleId(), 0.30, baseTime.plusSeconds(600)
        );
        ReplanningResult replanRes = replanningService.handleBatteryDrift(measuredInput, latestPlan, drift);
        long replanMs = replanRes.getElapsedDuration().toMillis();

        // 4. Validate latest plan
        ValidationReport report = validator.validate(latestPlan, measuredInput);

        int evAssigned = latestPlan.getEvAssignments().size();
        int backupCount = latestPlan.getTotalBackupTripCount();
        double coverage = (evAssigned * 100.0) / trips;
        double cost = latestPlan.getTotalChargingCost().doubleValue();

        return new BenchmarkResult(
            name, evs, trips, chargers,
            warmUpDuration, p50, p95, p99, replanMs,
            evAssigned, backupCount, coverage, cost,
            report.isValid(), report.getTotalChecksExecuted()
        );
    }

    @Test
    @DisplayName("Execute full 4-tier benchmark harness and verify targets")
    void executeFullBenchmarkSuite() throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();

        // Tier 1: Small (10 EVs, 50 Trips, 2 Hubs, 4 Chargers)
        results.add(runScenarioBenchmark("Small", 10, 50, 2, 4, 10));

        // Tier 2: Medium (50 EVs, 250 Trips, 5 Hubs, 15 Chargers)
        results.add(runScenarioBenchmark("Medium", 50, 250, 5, 15, 10));

        // Tier 3: Large (100 EVs, 500 Trips, 8 Hubs, 25 Chargers)
        results.add(runScenarioBenchmark("Large", 100, 500, 8, 25, 10));

        // Tier 4: Target (300 EVs, 1,500 Trips, 10 Hubs, 40 Chargers)
        BenchmarkResult targetResult = runScenarioBenchmark("Target", 300, 1500, 10, 40, 5);
        results.add(targetResult);

        // Verify hard performance targets
        assertThat(targetResult.planDurationP50Ms())
            .as("Full plan generation for 300 EVs / 1,500 trips must be under 30 seconds")
            .isLessThan(30000L);

        assertThat(targetResult.replanDurationP50Ms())
            .as("Live replanning must be under 2 seconds SLA")
            .isLessThan(2000L);

        for (BenchmarkResult res : results) {
            assertThat(res.validationPassed())
                .as("Safety validation must PASS without violations for scenario " + res.scenarioName())
                .isTrue();
        }

        // Print human-readable summary
        System.out.println();
        System.out.println("==================================================================================================================");
        System.out.println("                       URJAROUTE EV FLEET BENCHMARK HARNESS SUMMARY RESULTS                                       ");
        System.out.println("==================================================================================================================");
        System.out.printf("%-10s | %-4s | %-6s | %-8s | %-7s | %-7s | %-7s | %-8s | %-9s | %-10s | %-10s%n",
            "Tier", "EVs", "Trips", "Chargers", "p50 (ms)", "p95(ms)", "p99(ms)", "Replan", "Coverage", "Cost (INR)", "Validator");
        System.out.println("------------------------------------------------------------------------------------------------------------------");
        for (BenchmarkResult r : results) {
            System.out.printf("%-10s | %-4d | %-6d | %-8d | %-7d | %-7d | %-7d | %-6d ms | %-8.1f%% | ₹ %-8.2f | %-10s%n",
                r.scenarioName(), r.evCount(), r.tripCount(), r.chargerCount(),
                r.planDurationP50Ms(), r.planDurationP95Ms(), r.planDurationP99Ms(),
                r.replanDurationP50Ms(), r.serviceCoveragePct(), r.totalCostInr(),
                r.validationPassed() ? "PASS (" + r.totalValidationChecks() + ")" : "FAIL");
        }
        System.out.println("==================================================================================================================");

        // Output results to CSV
        try (PrintWriter writer = new PrintWriter(new FileWriter("target/benchmark-results.csv"))) {
            writer.println("Tier,EVs,Trips,Chargers,WarmupMs,PlanP50Ms,PlanP95Ms,PlanP99Ms,ReplanP50Ms,EVAssigned,BackupTrips,CoveragePct,CostINR,ValidatorPass,Checks");
            for (BenchmarkResult r : results) {
                writer.printf("%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%b,%d%n",
                    r.scenarioName(), r.evCount(), r.tripCount(), r.chargerCount(),
                    r.warmUpPlanDurationMs(), r.planDurationP50Ms(), r.planDurationP95Ms(), r.planDurationP99Ms(),
                    r.replanDurationP50Ms(), r.evAssignedCount(), r.backupTripCount(),
                    r.serviceCoveragePct(), r.totalCostInr(), r.validationPassed(), r.totalValidationChecks());
            }
        }
    }
}
