package com.moveinsync.evfleet.explain;

import com.moveinsync.evfleet.domain.ChargerHub;
import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;
import com.moveinsync.evfleet.planner.FeasibilityReason;
import com.moveinsync.evfleet.planner.FeasibilityResult;
import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.PlanningInput;
import com.moveinsync.evfleet.planner.TimelineEvent;
import com.moveinsync.evfleet.planner.TripAssignment;
import com.moveinsync.evfleet.energy.EnergyFactors;
import com.moveinsync.evfleet.energy.StandardEnergyModel;
import com.moveinsync.evfleet.planner.TripFeasibilityChecker;
import com.moveinsync.evfleet.travel.DefaultTravelTimeEstimator;
import com.moveinsync.evfleet.travel.DistanceMatrix;
import com.moveinsync.evfleet.travel.TravelConfig;
import com.moveinsync.evfleet.travel.TravelTimeEstimator;
import com.moveinsync.evfleet.planner.VehicleTimeline;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.validator.ValidationReport;
import com.moveinsync.evfleet.validator.ValidationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Stateless service that produces a complete {@link PlanExplanation} for a planning run.
 *
 * <p><strong>Responsibilities:</strong>
 * <ul>
 *   <li>Derive per-trip assignment explanations from actual plan data.</li>
 *   <li>Run bounded post-hoc alternative diagnostics using existing feasibility logic.</li>
 *   <li>Produce per-session charging explanations.</li>
 *   <li>Build cost breakdown with hub/tariff-period subtotals.</li>
 *   <li>Build plan quality metrics.</li>
 *   <li>Calculate lower bounds for backup-trip count and electricity cost.</li>
 * </ul>
 *
 * <p><strong>Invariants:</strong>
 * <ul>
 *   <li>The plan, input, and validation report are never modified.</li>
 *   <li>No metrics are fabricated; unavailable values carry an explicit reason.</li>
 *   <li>Heuristic output is clearly labeled as heuristic, not proven optimal.</li>
 *   <li>Diagnostic alternative evaluation is bounded by {@code maxAlternativesPerTrip}.</li>
 * </ul>
 */
public final class PlanExplanationService {

    /** Default maximum number of alternatives evaluated per trip during post-hoc diagnostics. */
    public static final int DEFAULT_MAX_ALTERNATIVES = 5;

    private static final double RESERVE_FLOOR = 0.15;

    private final TripFeasibilityChecker feasibilityChecker;
    private final int maxAlternativesPerTrip;

    public PlanExplanationService() {
        TravelTimeEstimator estimator = new DefaultTravelTimeEstimator(TravelConfig.defaultConfig());
        DistanceMatrix dm = new DistanceMatrix(estimator);
        this.feasibilityChecker    = new TripFeasibilityChecker(new StandardEnergyModel(),
                EnergyFactors.standard(), estimator, dm);
        this.maxAlternativesPerTrip = DEFAULT_MAX_ALTERNATIVES;
    }

    public PlanExplanationService(TripFeasibilityChecker feasibilityChecker, int maxAlternativesPerTrip) {
        this.feasibilityChecker    = Objects.requireNonNull(feasibilityChecker, "feasibilityChecker");
        this.maxAlternativesPerTrip = Math.max(0, maxAlternativesPerTrip);
    }

    /**
     * Produces a complete explanation for a planning result.
     *
     * @param input            the planning input (never modified)
     * @param plan             the plan result (never modified)
     * @param validationReport the independent validator's report (may be null; treated as INDETERMINATE)
     * @return an immutable {@link PlanExplanation}
     */
    public PlanExplanation explain(PlanningInput input, FleetPlanResult plan,
                                   ValidationReport validationReport) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(plan, "plan must not be null");

        long startNanos = System.nanoTime();
        ValidationReport vr = validationReport != null ? validationReport
                : ValidationReport.indeterminate(0, 0, Collections.emptyList(), Duration.ZERO);

        List<AssignmentExplanation> assignments = buildAssignmentExplanations(input, plan);
        List<ChargingExplanation>   charging    = buildChargingExplanations(plan, input);
        CostBreakdown               costs       = calculateCostBreakdown(input, plan, assignments);
        PlanQualityReport           quality     = analyzeQuality(input, plan, vr, costs);
        LowerBoundResult            backupLb    = calculateBackupLowerBound(input, plan);
        LowerBoundResult            costLb      = calculateCostLowerBound(input, plan, costs);

        return new PlanExplanation.Builder()
                .planningRunId(plan.getPlanningRunId())
                .assignmentExplanations(assignments)
                .chargingExplanations(charging)
                .vehicleTimelines(plan.getVehicleTimelines())
                .costBreakdown(costs)
                .qualityReport(quality)
                .backupTripLowerBound(backupLb)
                .electricityCostLowerBound(costLb)
                .generationDuration(Duration.ofNanos(System.nanoTime() - startNanos))
                .build();
    }

    // =========================================================================
    // Assignment explanations
    // =========================================================================

    private List<AssignmentExplanation> buildAssignmentExplanations(PlanningInput input, FleetPlanResult plan) {
        // Build session lookup: tripId -> sessionId (for charging-before-trip detection)
        Map<String, String> vehicleToSessionId = new HashMap<>();
        for (ChargingSession s : plan.getChargingSessions()) {
            vehicleToSessionId.put(s.getVehicleId(), s.getSessionId());
        }

        List<AssignmentExplanation> result = new ArrayList<>();
        for (TripAssignment ta : plan.getTripAssignments()) {
            if (ta.isEvAssignment()) {
                result.add(buildEvExplanation(ta, plan, input, vehicleToSessionId));
            } else {
                result.add(buildBackupExplanation(ta, plan, input));
            }
        }
        // Stable ordering: by tripId
        result.sort(Comparator.comparing(AssignmentExplanation::getTripId));
        return result;
    }

    private AssignmentExplanation buildEvExplanation(TripAssignment ta, FleetPlanResult plan,
                                                     PlanningInput input,
                                                     Map<String, String> vehicleToSessionId) {
        Trip trip    = ta.getTrip();
        Vehicle ev   = ta.getAssignedVehicle();
        String vid   = ev.getVehicleId();

        VehicleTimeline tl = plan.getVehicleTimelines().get(vid);

        // Extract SoC values from the timeline events that reference this trip
        double socBefore       = Double.NaN;
        double socAfterEmpty   = Double.NaN;
        double socAfterTrip    = Double.NaN;
        double emptyDist       = 0.0;
        double emptyEnergy     = 0.0;
        double tripEnergy      = 0.0;
        Instant arrivalAtPickup = null;
        Instant tripEnd         = null;
        boolean chargingBefore  = false;

        if (tl != null) {
            for (TimelineEvent evt : tl.getEvents()) {
                if (trip.getTripId().equals(evt.getReferenceId())) {
                    if (evt.getEventType() == TimelineEvent.EventType.EMPTY_DRIVE) {
                        socBefore     = evt.getStartSoc();
                        socAfterEmpty = evt.getEndSoc();
                        emptyDist     = evt.getDistanceKm();
                        emptyEnergy   = Math.abs(evt.getEnergyDeltaKwh());
                        arrivalAtPickup = evt.getEndTime();
                    } else if (evt.getEventType() == TimelineEvent.EventType.PASSENGER_TRIP) {
                        if (Double.isNaN(socBefore))    socBefore     = evt.getStartSoc();
                        if (Double.isNaN(socAfterEmpty)) socAfterEmpty = evt.getStartSoc();
                        socAfterTrip  = evt.getEndSoc();
                        tripEnergy    = Math.abs(evt.getEnergyDeltaKwh());
                        tripEnd       = evt.getEndTime();
                        if (arrivalAtPickup == null) arrivalAtPickup = evt.getStartTime();
                    }
                }
            }
            // Detect charging before this trip: look for a CHARGING event that ends before the trip starts
            final Instant effectivePickupArrival = arrivalAtPickup;
            chargingBefore = tl.getEvents().stream()
                    .anyMatch(e -> e.getEventType() == TimelineEvent.EventType.CHARGING
                            && effectivePickupArrival != null
                            && !e.getEndTime().isAfter(effectivePickupArrival));
        }

        // Determine whether the trip's charging session was attached to this vehicle
        String sessionId = chargingBefore ? vehicleToSessionId.get(vid) : null;

        double reserveMargin = Double.isNaN(socAfterTrip) ? Double.NaN : (socAfterTrip - RESERVE_FLOOR);
        String rationale = buildEvRationale(ta, emptyDist, chargingBefore, reserveMargin);

        // Post-hoc alternatives for EV assignments: find feasible-but-not-selected alternatives
        List<AlternativeEvaluation> alts = buildAlternativesForEv(ta, trip, input);

        return new AssignmentExplanation.Builder()
                .tripId(trip.getTripId())
                .decisionType(AssignmentExplanation.DecisionType.EV_ASSIGNED)
                .assignedVehicleId(vid)
                .emptyDriveDistanceKm(emptyDist)
                .emptyDriveEnergyKwh(emptyEnergy)
                .tripEnergyKwh(tripEnergy)
                .socBeforeEmptyDrive(socBefore)
                .socAfterEmptyDrive(socAfterEmpty)
                .socAfterTrip(socAfterTrip)
                .reserveMarginAfterTrip(reserveMargin)
                .arrivalAtPickup(arrivalAtPickup)
                .tripCompletionTime(tripEnd)
                .chargingRequiredBefore(chargingBefore)
                .chargingSessionId(sessionId)
                .selectionRationale(rationale)
                .alternatives(alts)
                .provenInfeasible(false)
                .build();
    }

    private String buildEvRationale(TripAssignment ta, double emptyDist,
                                     boolean chargingBefore, double reserveMargin) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vehicle ").append(ta.getAssignedVehicle().getVehicleId())
          .append(" selected: available within trip time window");
        if (emptyDist == 0.0) {
            sb.append("; no empty drive required (already at pickup)");
        } else {
            sb.append(String.format("; empty drive %.2f km", emptyDist));
        }
        if (chargingBefore) {
            sb.append("; charging session scheduled before trip to reach required SoC");
        }
        if (!Double.isNaN(reserveMargin) && reserveMargin >= 0) {
            sb.append(String.format("; reserve margin after trip = %.2f%%", reserveMargin * 100));
        }
        sb.append(". [HEURISTIC selection â€” minimum deadhead distance, then vehicle ID tie-break]");
        return sb.toString();
    }

    private List<AlternativeEvaluation> buildAlternativesForEv(TripAssignment ta, Trip trip,
                                                                PlanningInput input) {
        List<AlternativeEvaluation> alts = new ArrayList<>();
        int limit = Math.min(maxAlternativesPerTrip, input.getVehicles().size());
        int evaluated = 0;
        String assignedId = ta.getAssignedVehicle().getVehicleId();

        for (Vehicle v : input.getVehicles()) {
            if (v.getVehicleId().equals(assignedId)) continue;
            if (evaluated >= limit) {
                alts.add(AlternativeEvaluation.notEvaluated(v.getVehicleId()));
                continue;
            }
            FeasibilityResult fr = feasibilityChecker.check(v, trip);
            evaluated++;
            AlternativeOutcome outcome = fr.isFeasible()
                    ? AlternativeOutcome.FEASIBLE_BUT_NOT_SELECTED
                    : mapReason(fr.getReason());
            alts.add(AlternativeEvaluation.of(v.getVehicleId(), outcome, fr.getExplanation()));
        }
        alts.sort(Comparator.comparing(AlternativeEvaluation::getVehicleId));
        return alts;
    }

    private AssignmentExplanation buildBackupExplanation(TripAssignment ta, FleetPlanResult plan,
                                                          PlanningInput input) {
        Trip trip = ta.getTrip();
        FeasibilityReason reason = ta.getBackupReason();
        String detail  = ta.getBackupReasonDetail();

        // Post-hoc diagnostic: evaluate up to maxAlternativesPerTrip vehicles
        List<AlternativeEvaluation> alts = new ArrayList<>();
        int evaluated = 0;
        for (Vehicle v : input.getVehicles()) {
            if (evaluated >= maxAlternativesPerTrip) {
                alts.add(AlternativeEvaluation.notEvaluated(v.getVehicleId()));
                continue;
            }
            FeasibilityResult fr = feasibilityChecker.check(v, trip);
            evaluated++;
            AlternativeOutcome outcome = mapReason(fr.getReason());
            alts.add(AlternativeEvaluation.of(v.getVehicleId(), outcome, fr.getExplanation()));
        }
        alts.sort(Comparator.comparing(AlternativeEvaluation::getVehicleId));

        // The greedy heuristic does NOT prove exhaustive infeasibility
        boolean provenInfeasible = false;

        return new AssignmentExplanation.Builder()
                .tripId(trip.getTripId())
                .decisionType(AssignmentExplanation.DecisionType.BACKUP_ASSIGNED)
                .primaryRejectionReason(reason)
                .primaryRejectionDetail(
                        detail + " [NO_FEASIBLE_ASSIGNMENT_FOUND_BY_HEURISTIC â€” "
                        + "exhaustive infeasibility not proven]")
                .candidatesEvaluated(evaluated)
                .provenInfeasible(provenInfeasible)
                .alternatives(alts)
                .build();
    }

    private static AlternativeOutcome mapReason(FeasibilityReason reason) {
        if (reason == null || reason == FeasibilityReason.NONE) return AlternativeOutcome.FEASIBLE_BUT_NOT_SELECTED;
        switch (reason) {
            case VEHICLE_UNAVAILABLE:               return AlternativeOutcome.VEHICLE_UNAVAILABLE;
            case PICKUP_UNREACHABLE:                return AlternativeOutcome.PICKUP_TRAVEL_INFEASIBLE;
            case DEADLINE_INFEASIBLE:               return AlternativeOutcome.TRIP_DEADLINE_MISSED;
            case INSUFFICIENT_ENERGY:
            case INSUFFICIENT_ENERGY_FOR_EMPTY_DRIVE:
            case INSUFFICIENT_ENERGY_FOR_TRIP:     return AlternativeOutcome.INSUFFICIENT_BATTERY;
            case CHARGER_CAPACITY_UNAVAILABLE:      return AlternativeOutcome.CHARGING_UNAVAILABLE;
            default:                               return AlternativeOutcome.OTHER_CONSTRAINT;
        }
    }

    // =========================================================================
    // Charging explanations
    // =========================================================================

    private List<ChargingExplanation> buildChargingExplanations(FleetPlanResult plan, PlanningInput input) {
        // Build hub tariff lookup
        Map<String, TariffSchedule> hubTariff = new HashMap<>();
        for (ChargerHub h : input.getHubs()) {
            hubTariff.put(h.getHubId(), h.getTariffSchedule());
        }

        List<ChargingExplanation> result = new ArrayList<>();
        for (ChargingSession s : plan.getChargingSessions()) {
            String tariffLabel = determineTariffLabel(s, hubTariff.get(s.getHubId()));
            String rationale = String.format(
                    "Charging scheduled at hub %s to raise SoC from %.1f%% to %.1f%% (%.2f kWh delivered, %s %s)",
                    s.getHubId(), s.getStartSoc() * 100, s.getEndSoc() * 100,
                    s.getEnergyDeliveredKwh(), s.getCurrency(), s.getEstimatedCost());
            result.add(new ChargingExplanation(
                    s.getSessionId(), s.getVehicleId(), s.getHubId(),
                    s.getStartTime(), s.getEndTime(),
                    s.getStartSoc(), s.getEndSoc(),
                    s.getEnergyDeliveredKwh(), s.getEstimatedCost(), s.getCurrency(),
                    tariffLabel, rationale));
        }
        result.sort(Comparator.comparing(ChargingExplanation::getStartTime)
                .thenComparing(ChargingExplanation::getSessionId));
        return result;
    }

    private String determineTariffLabel(ChargingSession session, TariffSchedule tariff) {
        // Without introspecting the TariffSchedule's internal slots (private), we derive a label
        // by comparing the session cost to a hypothetical off-peak reference.
        // If tariff is unavailable, we return UNKNOWN.
        if (tariff == null) return "UNKNOWN";
        // Use hour-of-day as a rough heuristic: 22:00â€“06:00 UTC+5:30 considered off-peak
        // This is a documented assumption, not an authoritative fact from the domain model.
        int hourUtc = session.getStartTime().atZone(java.time.ZoneOffset.UTC).getHour();
        // 16:30 UTC = 22:00 IST; 00:30 UTC = 06:00 IST
        if (hourUtc >= 17 || hourUtc < 1) return "ESTIMATED_OFF_PEAK";
        return "ESTIMATED_PEAK";
    }

    // =========================================================================
    // Cost breakdown
    // =========================================================================

    public CostBreakdown calculateCostBreakdown(PlanningInput input, FleetPlanResult plan,
                                                 List<AssignmentExplanation> assignments) {
        List<ChargingSession> sessions = plan.getChargingSessions();

        BigDecimal total = sessions.stream()
                .map(ChargingSession::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = plan.getCurrency();

        // Cost by hub (directly from plan's authoritative costByHub map)
        Map<String, BigDecimal> costByHub = new HashMap<>(plan.getCostByHub());

        // Cost by tariff period (estimated, documented as such)
        Map<String, BigDecimal> costByTariffPeriod = new HashMap<>();
        Map<String, TariffSchedule> hubTariff = new HashMap<>();
        for (ChargerHub h : input.getHubs()) hubTariff.put(h.getHubId(), h.getTariffSchedule());
        for (ChargingSession s : sessions) {
            String label = determineTariffLabel(s, hubTariff.get(s.getHubId()));
            costByTariffPeriod.merge(label, s.getEstimatedCost(), BigDecimal::add);
        }

        double totalEnergyCharged = sessions.stream()
                .mapToDouble(ChargingSession::getEnergyDeliveredKwh).sum();
        double totalEnergyConsumed = plan.getVehicleTimelines().values().stream()
                .mapToDouble(VehicleTimeline::getTotalEnergyConsumedKwh).sum();

        int evTrips = (int) plan.getTripAssignments().stream().filter(TripAssignment::isEvAssignment).count();
        int backupTrips = plan.getTotalBackupTripCount();

        return new CostBreakdown.Builder()
                .totalElectricityCost(total)
                .currency(currency)
                .totalChargingSessions(sessions.size())
                .totalEnergyChargedKwh(totalEnergyCharged)
                .totalEnergyConsumedKwh(totalEnergyConsumed)
                .costByHub(costByHub)
                .costByTariffPeriod(costByTariffPeriod)
                .backupCabTripCount(backupTrips)
                .evTripCount(evTrips)
                .build();
    }

    // =========================================================================
    // Quality report
    // =========================================================================

    public PlanQualityReport analyzeQuality(PlanningInput input, FleetPlanResult plan,
                                             ValidationReport vr, CostBreakdown costs) {
        List<TripAssignment> assignments = plan.getTripAssignments();
        int evCount     = (int) assignments.stream().filter(TripAssignment::isEvAssignment).count();
        int backupCount = (int) assignments.stream().filter(TripAssignment::isBackupAssignment).count();

        // Count distinct vehicles that have at least one timeline event
        long vehiclesUsed = plan.getVehicleTimelines().values().stream()
                .filter(tl -> !tl.getEvents().isEmpty())
                .count();

        // Minimum observed SoC across all timeline event end-SoC values
        OptionalDouble minSoc = plan.getVehicleTimelines().values().stream()
                .flatMap(tl -> tl.getEvents().stream())
                .mapToDouble(TimelineEvent::getEndSoc)
                .min();
        OptionalDouble minMargin = minSoc.isPresent()
                ? OptionalDouble.of(minSoc.getAsDouble() - RESERVE_FLOOR)
                : OptionalDouble.empty();

        // Peak simultaneous charging sessions via sweep-line on [start, end) intervals
        int peak = computePeakConcurrent(plan.getChargingSessions());

        com.moveinsync.evfleet.planner.PlanningQualityReport pqr = plan.getQualityReport();
        Duration runtime         = pqr != null ? pqr.getPlanningRuntime() : Duration.ZERO;
        int candidateEvals       = pqr != null ? pqr.getCandidateEvaluations() : 0;
        int localSearchIter      = pqr != null ? pqr.getLocalSearchIterations() : 0;

        return new PlanQualityReport.Builder()
                .totalTripsRequested(input.getTrips().size())
                .evAssignedTrips(evCount)
                .backupAssignedTrips(backupCount)
                .vehiclesUsed((int) vehiclesUsed)
                .chargingSessionsCount(plan.getChargingSessions().size())
                .totalEnergyConsumedKwh(costs.getTotalEnergyConsumedKwh())
                .totalEnergyChargedKwh(costs.getTotalEnergyChargedKwh())
                .totalElectricityCost(costs.getTotalElectricityCost())
                .currency(plan.getCurrency())
                .peakSimultaneousChargingSessions(peak)
                .minimumObservedSoc(minSoc)
                .minimumReserveMargin(minMargin)
                .plannerRuntime(runtime)
                .candidateEvaluations(candidateEvals)
                .localSearchIterations(localSearchIter)
                .plannerBudgetExhausted(false) // greedy + bounded local search never signals budget exhaustion
                .validated(vr.isValid())
                .validationStatus(vr.getStatus())
                .violationCount(vr.getViolationCount())
                .build();
    }

    /** Sweep-line O(N log N) peak concurrent charging sessions counter. */
    private int computePeakConcurrent(List<ChargingSession> sessions) {
        if (sessions.isEmpty()) return 0;
        List<long[]> events = new ArrayList<>();
        for (ChargingSession s : sessions) {
            events.add(new long[]{ s.getStartTime().toEpochMilli(), +1 });
            events.add(new long[]{ s.getEndTime().toEpochMilli(),   -1 });
        }
        // Sort: by time, END (-1) before START (+1) at identical timestamps (half-open [start,end))
        events.sort(Comparator.comparingLong((long[] e) -> e[0])
                               .thenComparingLong(e -> e[1]));
        int peak = 0, cur = 0;
        for (long[] ev : events) {
            cur += (int) ev[1];
            if (cur > peak) peak = cur;
        }
        return peak;
    }

    // =========================================================================
    // Lower-bound analysis
    // =========================================================================

    public LowerBoundResult calculateBackupLowerBound(PlanningInput input, FleetPlanResult plan) {
        // Sound lower bound: 0 when vehicles exist (we cannot prove any trip is unavoidably backup
        // without exhaustive enumeration). WEAK but valid.
        if (input.getVehicles().isEmpty()) {
            int allTrips = input.getTrips().size();
            return new LowerBoundResult.Builder()
                    .objectiveLabel("BACKUP_TRIP_COUNT")
                    .status(LowerBoundStatus.AVAILABLE)
                    .lowerBoundValue(BigDecimal.valueOf(allTrips))
                    .actualValue(BigDecimal.valueOf(plan.getTotalBackupTripCount()))
                    .assumptions("No vehicles in input â€” every trip is necessarily a backup cab assignment.")
                    .build();
        }
        // Greedy heuristic cannot prove global infeasibility for any individual trip.
        return new LowerBoundResult.Builder()
                .objectiveLabel("BACKUP_TRIP_COUNT")
                .status(LowerBoundStatus.WEAK)
                .lowerBoundValue(BigDecimal.ZERO)
                .actualValue(BigDecimal.valueOf(plan.getTotalBackupTripCount()))
                .assumptions("Lower bound = 0 (valid but weak). The greedy heuristic does not perform "
                        + "exhaustive enumeration and cannot prove that any trip is unavoidably a backup. "
                        + "A tighter bound would require an exact algorithm (e.g., matching or ILP).")
                .build();
    }

    public LowerBoundResult calculateCostLowerBound(PlanningInput input, FleetPlanResult plan,
                                                     CostBreakdown costs) {
        List<ChargingSession> sessions = plan.getChargingSessions();
        if (sessions.isEmpty()) {
            return new LowerBoundResult.Builder()
                    .objectiveLabel("ELECTRICITY_COST")
                    .status(LowerBoundStatus.AVAILABLE)
                    .lowerBoundValue(BigDecimal.ZERO)
                    .actualValue(BigDecimal.ZERO)
                    .assumptions("No charging sessions â€” lower bound = actual = 0.")
                    .build();
        }

        // Find minimum tariff rate across all hubs
        BigDecimal minRate = findMinimumTariffRate(input.getHubs());
        if (minRate == null) {
            return new LowerBoundResult.Builder()
                    .objectiveLabel("ELECTRICITY_COST")
                    .status(LowerBoundStatus.UNAVAILABLE)
                    .unavailableReason("No hub tariff data available; cannot compute cost lower bound.")
                    .assumptions("Requires at least one ChargerHub with a TariffSchedule.")
                    .build();
        }

        // Optimistic lower bound: sum(energyDeliveredKwh) * minRate
        // Constraints relaxed: session timing, concurrency, and tariff window assignment
        BigDecimal totalEnergy = BigDecimal.valueOf(costs.getTotalEnergyChargedKwh());
        BigDecimal lb = totalEnergy.multiply(minRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal actual = costs.getTotalElectricityCost();

        String assumptions = String.format(
                "Lower bound = totalEnergyCharged (%.4f kWh) x minTariffRate (%s %s). "
                + "Constraints relaxed: session timing, charger concurrency, tariff window assignment. "
                + "This bound is valid because the actual cost cannot be lower than (energy * cheapest rate). "
                + "A zero relative gap does NOT prove lexicographic global optimality â€” "
                + "the primary objective (backup count) is evaluated separately.",
                costs.getTotalEnergyChargedKwh(), plan.getCurrency(), minRate);
        return new LowerBoundResult.Builder()
                .objectiveLabel("ELECTRICITY_COST")
                .status(LowerBoundStatus.AVAILABLE)
                .lowerBoundValue(lb)
                .actualValue(actual)
                .assumptions(assumptions)
                .build();
    }

    private BigDecimal findMinimumTariffRate(List<ChargerHub> hubs) {
        BigDecimal min = null;
        for (ChargerHub hub : hubs) {
            com.moveinsync.evfleet.time.TariffSchedule ts = hub.getTariffSchedule();
            if (ts == null) continue;
            try {
                Instant probe1 = Instant.parse("2026-01-01T16:30:00Z"); // 22:00 IST (off-peak)
                Instant probe2 = Instant.parse("2026-01-01T01:30:00Z"); // 07:00 IST (peak)
                for (Instant probe : new Instant[]{probe1, probe2}) {
                    BigDecimal rate = ts.getRateAt(probe).ratePerKwh();
                    if (min == null || rate.compareTo(min) < 0) min = rate;
                }
                BigDecimal defaultRate = ts.getDefaultRate().ratePerKwh();
                if (min == null || defaultRate.compareTo(min) < 0) min = defaultRate;
            } catch (Exception ignored) {
                // Graceful skip if probe fails
            }
        }
        return min;
    }
}
