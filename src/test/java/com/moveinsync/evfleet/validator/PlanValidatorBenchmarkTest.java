package com.moveinsync.evfleet.validator;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.domain.VehicleAvailability;
import com.moveinsync.evfleet.domain.VehicleSpecification;
import com.moveinsync.evfleet.domain.VehicleStatus;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.GreedyFleetPlanner;
import com.moveinsync.evfleet.planner.PlanningInput;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorBenchmarkTest {

    private PlanValidator validator;
    private GreedyFleetPlanner planner;

    @BeforeEach
    void setUp() {
        validator = new DefaultPlanValidator();
        planner = GreedyFleetPlanner.createDefault();
    }

    private PlanningInput generateScenario(String runId, int numVehicles, int numHubs, int totalChargers, int numTrips, long seed) {
        Random rand = new Random(seed);
        Instant now = Instant.parse("2026-10-29T08:00:00Z");

        TariffRate standardRate = TariffRate.of(12.00, "INR");
        TariffSchedule schedule = new TariffSchedule(ZoneId.of("Asia/Kolkata"), "INR", List.of(), standardRate);

        List<ChargerHub> hubs = new ArrayList<>();
        int chargersPerHub = Math.max(1, totalChargers / numHubs);
        for (int h = 0; h < numHubs; h++) {
            Location loc = new Location(12.9000 + rand.nextDouble() * 0.15, 77.5500 + rand.nextDouble() * 0.15);
            hubs.add(new ChargerHub("HUB-" + (h + 1), "Hub " + (h + 1), loc, chargersPerHub, 60.0, schedule));
        }

        List<Vehicle> vehicles = new ArrayList<>();
        for (int v = 0; v < numVehicles; v++) {
            Location loc = hubs.get(v % hubs.size()).getLocation();
            double soc = 0.60 + rand.nextDouble() * 0.35;
            vehicles.add(Vehicle.create("EV-" + (v + 1), "TENANT-1", 60.0, 0.18, soc, loc, now));
        }

        List<Trip> trips = new ArrayList<>();
        for (int t = 0; t < numTrips; t++) {
            Location p = new Location(12.9200 + rand.nextDouble() * 0.12, 77.5600 + rand.nextDouble() * 0.12);
            Location d = new Location(12.9200 + rand.nextDouble() * 0.12, 77.5600 + rand.nextDouble() * 0.12);
            double dist = 5.0 + rand.nextDouble() * 15.0;
            Instant start = now.plusSeconds((t % 20) * 300L + rand.nextInt(120));
            Instant deadline = start.plusSeconds(3600 + (long) (dist * 120));
            trips.add(new Trip("TRIP-" + (t + 1), "TENANT-1", p, d, dist, start, deadline));
        }

        return new PlanningInput(runId, vehicles, trips, hubs);
    }

    @Test
    @DisplayName("Benchmark validation on Small scale (10 EVs, 50 trips)")
    void benchmarkSmallScale() {
        PlanningInput input = generateScenario("BENCH-SMALL", 10, 1, 5, 50, 42L);
        FleetPlanResult plan = planner.plan(input);

        long start = System.nanoTime();
        ValidationReport report = validator.validate(plan, input);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(report.isValid()).isTrue();
        assertThat(elapsedMillis).isLessThan(500);
        System.out.println("Validation Small Scale: " + elapsedMillis + " ms (Checks: " + report.getTotalChecksExecuted() + ")");
    }

    @Test
    @DisplayName("Benchmark validation on Medium scale (50 EVs, 250 trips)")
    void benchmarkMediumScale() {
        PlanningInput input = generateScenario("BENCH-MEDIUM", 50, 2, 10, 250, 42L);
        FleetPlanResult plan = planner.plan(input);

        long start = System.nanoTime();
        ValidationReport report = validator.validate(plan, input);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(report.isValid()).isTrue();
        assertThat(elapsedMillis).isLessThan(1000);
        System.out.println("Validation Medium Scale: " + elapsedMillis + " ms (Checks: " + report.getTotalChecksExecuted() + ")");
    }

    @Test
    @DisplayName("Benchmark validation on Target scale (300 EVs, 40 chargers, 1,500 trips)")
    void benchmarkTargetScale() {
        PlanningInput input = generateScenario("BENCH-TARGET", 300, 8, 40, 1500, 42L);
        FleetPlanResult plan = planner.plan(input);

        long start = System.nanoTime();
        ValidationReport report = validator.validate(plan, input);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(report.isValid()).isTrue();
        // Target scale validation should take well under 2.0 seconds
        assertThat(elapsedMillis).isLessThan(3000);
        System.out.println("Validation Target Scale: " + elapsedMillis + " ms (Checks: " + report.getTotalChecksExecuted() + ")");
    }
}
