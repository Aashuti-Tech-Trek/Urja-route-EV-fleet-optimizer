package com.moveinsync.evfleet.replan;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.GreedyFleetPlanner;
import com.moveinsync.evfleet.planner.PlanningInput;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.validator.DefaultPlanValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 5: Replanning Scale & Latency Benchmarks")
class ReplanningBenchmarkTest {

    private final Instant baseTime = Instant.parse("2026-10-29T08:00:00Z");
    private final Location locCenter = new Location(12.9716, 77.5946);

    private PlanningInput generateScenario(int numVehicles, int numTrips, int numHubs) {
        List<Vehicle> vehicles = new ArrayList<>(numVehicles);
        for (int i = 0; i < numVehicles; i++) {
            Location loc = new Location(12.9716 + (i * 0.001), 77.5946 + (i * 0.001));
            vehicles.add(Vehicle.create("EV-" + i, "TENANT-1", 60.0, 0.20, 0.70 + (i % 20) * 0.01, loc, baseTime));
        }

        List<Trip> trips = new ArrayList<>(numTrips);
        for (int i = 0; i < numTrips; i++) {
            Location pickup = new Location(12.9500 + (i % 30) * 0.002, 77.5800 + (i % 30) * 0.002);
            Location drop = new Location(12.9800 + (i % 30) * 0.002, 77.6200 + (i % 30) * 0.002);
            Instant start = baseTime.plusSeconds(300 + (i % 10) * 600);
            Instant deadline = start.plusSeconds(3600);
            trips.add(new Trip("T-" + i, "TENANT-1", pickup, drop, 10.0 + (i % 5), start, deadline));
        }

        List<ChargerHub> hubs = new ArrayList<>(numHubs);
        TariffSchedule schedule = new TariffSchedule(
                ZoneId.of("Asia/Kolkata"), "INR", List.of(), TariffRate.of(8.50, "INR"));
        for (int i = 0; i < numHubs; i++) {
            Location hLoc = new Location(12.9600 + i * 0.01, 77.6000 + i * 0.01);
            hubs.add(new ChargerHub("HUB-" + i, "TENANT-1", hLoc, 4, 50.0, schedule));
        }

        return new PlanningInput("BENCH-" + numVehicles, vehicles, trips, hubs);
    }

    @Test
    @DisplayName("Benchmark: 10 EVs / 50 Trips Replanning SLA < 2.0s")
    void benchmarkSmallReplanning() {
        PlanningInput input = generateScenario(10, 50, 2);
        GreedyFleetPlanner planner = GreedyFleetPlanner.createDefault();
        DefaultPlanValidator validator = new DefaultPlanValidator();
        ReplanningService service = new ReplanningService(planner, validator, Duration.ofMillis(2000));

        FleetPlanResult plan = planner.plan(input);
        BatteryDriftEvent drift = new BatteryDriftEvent("DRIFT-SMALL", "EV-0", 0.35, baseTime.plusSeconds(600));

        ReplanningResult res = service.handleBatteryDrift(input, plan, drift);
        System.out.printf("Replanning 10 EVs / 50 Trips: %d ms, Outcome: %s%n",
                res.getElapsedDuration().toMillis(), res.getOutcome());

        assertThat(res.getElapsedDuration().toMillis()).isLessThan(2000);
        assertThat(res.isTimeBudgetExceeded()).isFalse();
    }

    @Test
    @DisplayName("Benchmark: 50 EVs / 200 Trips Replanning SLA < 2.0s")
    void benchmarkMediumReplanning() {
        PlanningInput input = generateScenario(50, 200, 5);
        GreedyFleetPlanner planner = GreedyFleetPlanner.createDefault();
        GreedyFleetPlanner replanner = GreedyFleetPlanner.createFastReplanner();
        DefaultPlanValidator validator = new DefaultPlanValidator();
        ReplanningService service = new ReplanningService(replanner, validator, Duration.ofMillis(2000));

        FleetPlanResult plan = planner.plan(input);
        BatteryDriftEvent drift = new BatteryDriftEvent("DRIFT-MED", "EV-2", 0.30, baseTime.plusSeconds(600));

        ReplanningResult res = service.handleBatteryDrift(input, plan, drift);
        System.out.printf("Replanning 50 EVs / 200 Trips: %d ms, Outcome: %s%n",
                res.getElapsedDuration().toMillis(), res.getOutcome());

        assertThat(res.getElapsedDuration().toMillis()).isLessThan(2000);
        assertThat(res.isTimeBudgetExceeded()).isFalse();
    }
}
