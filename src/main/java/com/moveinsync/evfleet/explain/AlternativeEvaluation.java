package com.moveinsync.evfleet.explain;

import java.util.Objects;

/**
 * Immutable record of an alternative vehicle that was evaluated (or not evaluated) for a trip.
 */
public final class AlternativeEvaluation {

    private final String vehicleId;
    private final AlternativeOutcome outcome;
    private final String detail;

    private AlternativeEvaluation(String vehicleId, AlternativeOutcome outcome, String detail) {
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        this.outcome   = Objects.requireNonNull(outcome,    "outcome");
        this.detail    = detail != null ? detail : "";
    }

    public static AlternativeEvaluation of(String vehicleId, AlternativeOutcome outcome, String detail) {
        return new AlternativeEvaluation(vehicleId, outcome, detail);
    }

    public static AlternativeEvaluation notEvaluated(String vehicleId) {
        return new AlternativeEvaluation(vehicleId, AlternativeOutcome.NOT_EVALUATED,
                "Vehicle not evaluated — diagnostic limit reached or not a planner candidate");
    }

    public String getVehicleId()        { return vehicleId; }
    public AlternativeOutcome getOutcome() { return outcome; }
    public String getDetail()           { return detail; }

    @Override
    public String toString() {
        return String.format("AlternativeEvaluation[vehicle=%s, outcome=%s, detail=%s]",
                vehicleId, outcome, detail);
    }
}
