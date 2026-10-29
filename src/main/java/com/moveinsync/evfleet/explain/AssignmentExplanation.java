package com.moveinsync.evfleet.explain;

import com.moveinsync.evfleet.planner.FeasibilityReason;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, structured explanation for one trip assignment decision.
 *
 * <p>For EV assignments all planning values are present.
 * For backup assignments only trip, backup fields, and alternatives are populated.
 * Values are derived strictly from actual plan data; nothing is fabricated.
 */
public final class AssignmentExplanation {

    public enum DecisionType { EV_ASSIGNED, BACKUP_ASSIGNED }

    // ---- common ----
    private final String tripId;
    private final DecisionType decisionType;
    private final List<AlternativeEvaluation> alternatives;

    // ---- EV-assignment fields (null/NaN for BACKUP) ----
    private final String assignedVehicleId;
    private final double emptyDriveDistanceKm;
    private final double emptyDriveEnergyKwh;
    private final double tripEnergyKwh;
    private final double socBeforeEmptyDrive;
    private final double socAfterEmptyDrive;
    private final double socAfterTrip;
    private final double reserveMarginAfterTrip;
    private final Instant arrivalAtPickup;
    private final Instant tripCompletionTime;
    private final boolean chargingRequiredBefore;
    private final String chargingSessionId;
    private final String selectionRationale;

    // ---- backup-assignment fields (null for EV) ----
    private final FeasibilityReason primaryRejectionReason;
    private final String primaryRejectionDetail;
    private final int candidatesEvaluated;
    private final boolean provenInfeasible;

    private AssignmentExplanation(Builder b) {
        this.tripId                   = Objects.requireNonNull(b.tripId, "tripId");
        this.decisionType             = Objects.requireNonNull(b.decisionType, "decisionType");
        this.alternatives             = b.alternatives != null ? List.copyOf(b.alternatives) : Collections.emptyList();
        this.assignedVehicleId        = b.assignedVehicleId;
        this.emptyDriveDistanceKm     = b.emptyDriveDistanceKm;
        this.emptyDriveEnergyKwh      = b.emptyDriveEnergyKwh;
        this.tripEnergyKwh            = b.tripEnergyKwh;
        this.socBeforeEmptyDrive      = b.socBeforeEmptyDrive;
        this.socAfterEmptyDrive       = b.socAfterEmptyDrive;
        this.socAfterTrip             = b.socAfterTrip;
        this.reserveMarginAfterTrip   = b.reserveMarginAfterTrip;
        this.arrivalAtPickup          = b.arrivalAtPickup;
        this.tripCompletionTime       = b.tripCompletionTime;
        this.chargingRequiredBefore   = b.chargingRequiredBefore;
        this.chargingSessionId        = b.chargingSessionId;
        this.selectionRationale       = b.selectionRationale != null ? b.selectionRationale : "";
        this.primaryRejectionReason   = b.primaryRejectionReason;
        this.primaryRejectionDetail   = b.primaryRejectionDetail;
        this.candidatesEvaluated      = b.candidatesEvaluated;
        this.provenInfeasible         = b.provenInfeasible;
    }

    public boolean isEvAssignment()     { return decisionType == DecisionType.EV_ASSIGNED; }
    public boolean isBackupAssignment() { return decisionType == DecisionType.BACKUP_ASSIGNED; }

    public String getTripId()           { return tripId; }
    public DecisionType getDecisionType() { return decisionType; }
    public List<AlternativeEvaluation> getAlternatives() { return alternatives; }

    // EV-only getters — empty Optional for backup assignments
    public Optional<String>  getAssignedVehicleId()      { return Optional.ofNullable(assignedVehicleId); }
    public Optional<Double>  getEmptyDriveDistanceKm()   { return isEvAssignment() ? Optional.of(emptyDriveDistanceKm) : Optional.empty(); }
    public Optional<Double>  getEmptyDriveEnergyKwh()    { return isEvAssignment() ? Optional.of(emptyDriveEnergyKwh)  : Optional.empty(); }
    public Optional<Double>  getTripEnergyKwh()          { return isEvAssignment() ? Optional.of(tripEnergyKwh)         : Optional.empty(); }
    public Optional<Double>  getSocBeforeEmptyDrive()    { return isEvAssignment() ? Optional.of(socBeforeEmptyDrive)   : Optional.empty(); }
    public Optional<Double>  getSocAfterEmptyDrive()     { return isEvAssignment() ? Optional.of(socAfterEmptyDrive)    : Optional.empty(); }
    public Optional<Double>  getSocAfterTrip()           { return isEvAssignment() ? Optional.of(socAfterTrip)          : Optional.empty(); }
    public Optional<Double>  getReserveMarginAfterTrip() { return isEvAssignment() ? Optional.of(reserveMarginAfterTrip) : Optional.empty(); }
    public Optional<Instant> getArrivalAtPickup()        { return Optional.ofNullable(arrivalAtPickup); }
    public Optional<Instant> getTripCompletionTime()     { return Optional.ofNullable(tripCompletionTime); }
    public boolean           isChargingRequiredBefore()  { return chargingRequiredBefore; }
    public Optional<String>  getChargingSessionId()      { return Optional.ofNullable(chargingSessionId); }
    public String            getSelectionRationale()     { return selectionRationale; }

    // Backup-only getters
    public Optional<FeasibilityReason> getPrimaryRejectionReason()  { return Optional.ofNullable(primaryRejectionReason); }
    public Optional<String>            getPrimaryRejectionDetail()   { return Optional.ofNullable(primaryRejectionDetail); }
    public int                         getCandidatesEvaluated()      { return candidatesEvaluated; }
    /**
     * Returns true only when the heuristic has performed exhaustive analysis and can prove
     * that no EV assignment is possible.  Currently always false for the greedy heuristic,
     * which does not perform exhaustive search.
     */
    public boolean isProvenInfeasible() { return provenInfeasible; }

    @Override
    public String toString() {
        if (isEvAssignment()) {
            return String.format("AssignmentExplanation[EV, trip=%s, vehicle=%s, soc(before/after)=%.3f/%.3f, rationale=%s]",
                    tripId, assignedVehicleId, socBeforeEmptyDrive, socAfterTrip, selectionRationale);
        }
        return String.format("AssignmentExplanation[BACKUP, trip=%s, reason=%s, provenInfeasible=%s, candidatesEval=%d]",
                tripId, primaryRejectionReason, provenInfeasible, candidatesEvaluated);
    }

    // ---- Builder ----
    public static final class Builder {
        private String tripId;
        private DecisionType decisionType;
        private List<AlternativeEvaluation> alternatives;
        private String assignedVehicleId;
        private double emptyDriveDistanceKm;
        private double emptyDriveEnergyKwh;
        private double tripEnergyKwh;
        private double socBeforeEmptyDrive;
        private double socAfterEmptyDrive;
        private double socAfterTrip;
        private double reserveMarginAfterTrip;
        private Instant arrivalAtPickup;
        private Instant tripCompletionTime;
        private boolean chargingRequiredBefore;
        private String chargingSessionId;
        private String selectionRationale;
        private FeasibilityReason primaryRejectionReason;
        private String primaryRejectionDetail;
        private int candidatesEvaluated;
        private boolean provenInfeasible;

        public Builder tripId(String v)                              { tripId = v; return this; }
        public Builder decisionType(DecisionType v)                  { decisionType = v; return this; }
        public Builder alternatives(List<AlternativeEvaluation> v)   { alternatives = v; return this; }
        public Builder assignedVehicleId(String v)                   { assignedVehicleId = v; return this; }
        public Builder emptyDriveDistanceKm(double v)                { emptyDriveDistanceKm = v; return this; }
        public Builder emptyDriveEnergyKwh(double v)                 { emptyDriveEnergyKwh = v; return this; }
        public Builder tripEnergyKwh(double v)                       { tripEnergyKwh = v; return this; }
        public Builder socBeforeEmptyDrive(double v)                 { socBeforeEmptyDrive = v; return this; }
        public Builder socAfterEmptyDrive(double v)                  { socAfterEmptyDrive = v; return this; }
        public Builder socAfterTrip(double v)                        { socAfterTrip = v; return this; }
        public Builder reserveMarginAfterTrip(double v)              { reserveMarginAfterTrip = v; return this; }
        public Builder arrivalAtPickup(Instant v)                    { arrivalAtPickup = v; return this; }
        public Builder tripCompletionTime(Instant v)                 { tripCompletionTime = v; return this; }
        public Builder chargingRequiredBefore(boolean v)             { chargingRequiredBefore = v; return this; }
        public Builder chargingSessionId(String v)                   { chargingSessionId = v; return this; }
        public Builder selectionRationale(String v)                  { selectionRationale = v; return this; }
        public Builder primaryRejectionReason(FeasibilityReason v)   { primaryRejectionReason = v; return this; }
        public Builder primaryRejectionDetail(String v)              { primaryRejectionDetail = v; return this; }
        public Builder candidatesEvaluated(int v)                    { candidatesEvaluated = v; return this; }
        public Builder provenInfeasible(boolean v)                   { provenInfeasible = v; return this; }

        public AssignmentExplanation build() { return new AssignmentExplanation(this); }
    }
}
