package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.ChargingSession;
import com.moveinsync.evfleet.location.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of evaluating or scheduling a charging opportunity for a vehicle at a hub.
 */
public final class ScheduledChargingPlan {

    private final boolean feasible;
    private final FeasibilityReason infeasibilityReason;
    private final String explanation;
    private final ChargingSession session;
    private final TimelineEvent deadheadEvent; // null if vehicle was already at hub
    private final TimelineEvent chargingEvent;
    private final double resultingSoc;
    private final Instant availableTime;
    private final Location endLocation;

    private ScheduledChargingPlan(boolean feasible, FeasibilityReason infeasibilityReason, String explanation,
                                  ChargingSession session, TimelineEvent deadheadEvent,
                                  TimelineEvent chargingEvent, double resultingSoc,
                                  Instant availableTime, Location endLocation) {
        this.feasible = feasible;
        this.infeasibilityReason = infeasibilityReason != null ? infeasibilityReason : FeasibilityReason.NONE;
        this.explanation = explanation != null ? explanation : "";
        this.session = session;
        this.deadheadEvent = deadheadEvent;
        this.chargingEvent = chargingEvent;
        this.resultingSoc = resultingSoc;
        this.availableTime = availableTime;
        this.endLocation = endLocation;
    }

    public static ScheduledChargingPlan feasible(ChargingSession session, TimelineEvent deadheadEvent,
                                                 TimelineEvent chargingEvent, double resultingSoc,
                                                 Instant availableTime, Location endLocation) {
        return new ScheduledChargingPlan(true, FeasibilityReason.NONE, "Charging feasible",
                Objects.requireNonNull(session, "session"),
                deadheadEvent,
                Objects.requireNonNull(chargingEvent, "chargingEvent"),
                resultingSoc,
                Objects.requireNonNull(availableTime, "availableTime"),
                Objects.requireNonNull(endLocation, "endLocation"));
    }

    public static ScheduledChargingPlan infeasible(FeasibilityReason reason, String explanation) {
        return new ScheduledChargingPlan(false, reason, explanation,
                null, null, null, 0.0, null, null);
    }

    public boolean isFeasible() { return feasible; }
    public FeasibilityReason getInfeasibilityReason() { return infeasibilityReason; }
    public String getExplanation() { return explanation; }
    public Optional<ChargingSession> getSession() { return Optional.ofNullable(session); }
    public Optional<TimelineEvent> getDeadheadEvent() { return Optional.ofNullable(deadheadEvent); }
    public Optional<TimelineEvent> getChargingEvent() { return Optional.ofNullable(chargingEvent); }
    public double getResultingSoc() { return resultingSoc; }
    public Optional<Instant> getAvailableTime() { return Optional.ofNullable(availableTime); }
    public Optional<Location> getEndLocation() { return Optional.ofNullable(endLocation); }
}
