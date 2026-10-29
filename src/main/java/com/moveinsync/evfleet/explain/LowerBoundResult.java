package com.moveinsync.evfleet.explain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a lower-bound analysis for one optimization objective.
 *
 * <p><strong>Backup-trip lower bound:</strong>
 * A provably sound bound uses a subset of trips that cannot be served by any EV under the
 * given input constraints.  When the heuristic is unable to prove infeasibility, a weak
 * lower bound of 0 is reported — valid but not useful.
 *
 * <p><strong>Electricity-cost lower bound:</strong>
 * Computed as: sum over all charging sessions of (energyDeliveredKwh * minTariffRateInSchedule).
 * Assumptions:
 * <ul>
 *   <li>Every kWh could theoretically be scheduled in the cheapest tariff band.</li>
 *   <li>Charger concurrency and session timing constraints are relaxed.</li>
 *   <li>No additional charging energy is assumed beyond what is in the plan.</li>
 * </ul>
 * Because tariff constraints are relaxed, this is always ≤ the true minimum achievable cost,
 * making it a valid (though optimistic) lower bound.
 *
 * <p><strong>Zero-gap caveat:</strong>
 * A zero gap for the relaxed electricity cost bound does NOT prove lexicographic global optimality.
 * The primary backup-trip objective is evaluated separately.
 */
public final class LowerBoundResult {

    private final String objectiveLabel;
    private final LowerBoundStatus status;
    private final BigDecimal lowerBoundValue;   // null when UNAVAILABLE
    private final BigDecimal actualValue;       // null when UNAVAILABLE
    private final String assumptions;
    private final String unavailableReason;     // null when status != UNAVAILABLE

    private LowerBoundResult(Builder b) {
        this.objectiveLabel    = Objects.requireNonNull(b.objectiveLabel, "objectiveLabel");
        this.status            = Objects.requireNonNull(b.status, "status");
        this.lowerBoundValue   = b.lowerBoundValue;
        this.actualValue       = b.actualValue;
        this.assumptions       = b.assumptions != null ? b.assumptions : "";
        this.unavailableReason = b.unavailableReason;
    }

    public String getObjectiveLabel()            { return objectiveLabel; }
    public LowerBoundStatus getStatus()          { return status; }
    public Optional<BigDecimal> getLowerBoundValue() { return Optional.ofNullable(lowerBoundValue); }
    public Optional<BigDecimal> getActualValue()     { return Optional.ofNullable(actualValue); }
    public String getAssumptions()               { return assumptions; }
    public Optional<String> getUnavailableReason() { return Optional.ofNullable(unavailableReason); }

    /** Absolute gap = actual - lowerBound. Empty when either value is unavailable. */
    public Optional<BigDecimal> getAbsoluteGap() {
        if (lowerBoundValue == null || actualValue == null) return Optional.empty();
        return Optional.of(actualValue.subtract(lowerBoundValue));
    }

    /**
     * Relative gap = (actual - lowerBound) / lowerBound.
     * Returns empty when lowerBound is zero (division-by-zero avoided) or either value unavailable.
     */
    public Optional<BigDecimal> getRelativeGap() {
        if (lowerBoundValue == null || actualValue == null) return Optional.empty();
        if (lowerBoundValue.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        BigDecimal gap = actualValue.subtract(lowerBoundValue);
        return Optional.of(gap.divide(lowerBoundValue, 6, RoundingMode.HALF_UP));
    }

    /** True only when the gap is exactly zero AND the status is AVAILABLE (not WEAK). */
    public boolean isZeroGapAndAvailable() {
        return status == LowerBoundStatus.AVAILABLE
                && getAbsoluteGap().map(g -> g.compareTo(BigDecimal.ZERO) == 0).orElse(false);
    }

    @Override
    public String toString() {
        if (status == LowerBoundStatus.UNAVAILABLE) {
            return String.format("LowerBoundResult[%s, UNAVAILABLE: %s]", objectiveLabel, unavailableReason);
        }
        return String.format("LowerBoundResult[%s, %s, lb=%s, actual=%s, gap=%s]",
                objectiveLabel, status, lowerBoundValue, actualValue,
                getAbsoluteGap().map(Object::toString).orElse("N/A"));
    }

    public static final class Builder {
        private String objectiveLabel;
        private LowerBoundStatus status;
        private BigDecimal lowerBoundValue;
        private BigDecimal actualValue;
        private String assumptions;
        private String unavailableReason;

        public Builder objectiveLabel(String v)    { objectiveLabel = v; return this; }
        public Builder status(LowerBoundStatus v)  { status = v; return this; }
        public Builder lowerBoundValue(BigDecimal v) { lowerBoundValue = v; return this; }
        public Builder actualValue(BigDecimal v)   { actualValue = v; return this; }
        public Builder assumptions(String v)       { assumptions = v; return this; }
        public Builder unavailableReason(String v) { unavailableReason = v; return this; }
        public LowerBoundResult build()            { return new LowerBoundResult(this); }
    }
}
