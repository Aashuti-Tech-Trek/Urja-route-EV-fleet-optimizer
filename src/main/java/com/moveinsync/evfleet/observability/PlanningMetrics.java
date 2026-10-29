package com.moveinsync.evfleet.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Central registry of custom Micrometer metrics for the UrjaRoute planning engine.
 *
 * <p>All domain-specific meters are created here to ensure consistent naming,
 * tagging, and SLA boundary configuration across the application.</p>
 *
 * <h3>Meters</h3>
 * <ul>
 *   <li>{@code urjaroute.planning.duration} — timer for full planning cycles</li>
 *   <li>{@code urjaroute.replanning.duration} — timer for live replanning cycles</li>
 *   <li>{@code urjaroute.planning.trips.assigned} — counter of successfully assigned trips</li>
 *   <li>{@code urjaroute.planning.trips.unassigned} — counter of trips that could not be assigned</li>
 *   <li>{@code urjaroute.planning.candidates.evaluated} — summary of candidate evaluations per planning run</li>
 *   <li>{@code urjaroute.planning.cost.total} — summary of total plan cost</li>
 *   <li>{@code urjaroute.planning.invocations} — counter of planning invocations by outcome</li>
 * </ul>
 */
@Component
public class PlanningMetrics {

    private final Timer planningDuration;
    private final Timer replanningDuration;
    private final Counter tripsAssigned;
    private final Counter tripsUnassigned;
    private final DistributionSummary candidatesEvaluated;
    private final DistributionSummary totalCost;
    private final Counter planningSuccess;
    private final Counter planningFailure;

    public PlanningMetrics(MeterRegistry registry) {
        this.planningDuration = Timer.builder("urjaroute.planning.duration")
                .description("Time taken for a full planning cycle")
                .publishPercentiles(0.5, 0.95, 0.99)
                .sla(Duration.ofSeconds(30))
                .register(registry);

        this.replanningDuration = Timer.builder("urjaroute.replanning.duration")
                .description("Time taken for a live replanning cycle")
                .publishPercentiles(0.5, 0.95, 0.99)
                .sla(Duration.ofSeconds(2))
                .register(registry);

        this.tripsAssigned = Counter.builder("urjaroute.planning.trips.assigned")
                .description("Number of trips successfully assigned across all planning runs")
                .register(registry);

        this.tripsUnassigned = Counter.builder("urjaroute.planning.trips.unassigned")
                .description("Number of trips that could not be assigned")
                .register(registry);

        this.candidatesEvaluated = DistributionSummary.builder("urjaroute.planning.candidates.evaluated")
                .description("Number of candidate evaluations per planning run")
                .publishPercentiles(0.5, 0.95)
                .register(registry);

        this.totalCost = DistributionSummary.builder("urjaroute.planning.cost.total")
                .description("Total cost of generated plans")
                .publishPercentiles(0.5, 0.95)
                .register(registry);

        this.planningSuccess = Counter.builder("urjaroute.planning.invocations")
                .tag("outcome", "success")
                .description("Successful planning invocations")
                .register(registry);

        this.planningFailure = Counter.builder("urjaroute.planning.invocations")
                .tag("outcome", "failure")
                .description("Failed planning invocations")
                .register(registry);
    }

    /** Record the duration of a full planning cycle. */
    public <T> T recordPlanningDuration(Callable<T> task) throws Exception {
        return planningDuration.recordCallable(task);
    }

    /** Record the duration of a live replanning cycle. */
    public <T> T recordReplanningDuration(Callable<T> task) throws Exception {
        return replanningDuration.recordCallable(task);
    }

    /** Increment the counter of successfully assigned trips. */
    public void recordTripsAssigned(int count) {
        tripsAssigned.increment(count);
    }

    /** Increment the counter of unassigned trips. */
    public void recordTripsUnassigned(int count) {
        tripsUnassigned.increment(count);
    }

    /** Record the number of candidates evaluated in a planning run. */
    public void recordCandidatesEvaluated(long count) {
        candidatesEvaluated.record(count);
    }

    /** Record the total cost of a generated plan. */
    public void recordTotalCost(double cost) {
        totalCost.record(cost);
    }

    /** Record a successful planning invocation. */
    public void recordSuccess() {
        planningSuccess.increment();
    }

    /** Record a failed planning invocation. */
    public void recordFailure() {
        planningFailure.increment();
    }
}
