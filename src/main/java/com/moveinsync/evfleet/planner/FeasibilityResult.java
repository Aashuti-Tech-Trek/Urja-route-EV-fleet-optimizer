package com.moveinsync.evfleet.planner;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, structured result of a trip feasibility evaluation.
 *
 * <p>A result is either {@link FeasibilityStatus#FEASIBLE} or {@link FeasibilityStatus#INFEASIBLE}.
 * For infeasible results, a stable {@link FeasibilityReason} and a human-readable explanation
 * are always provided. For feasible results, all computed planning values are available.
 *
 * <p>Use the static factory methods {@link #feasible} and {@link #infeasible} to construct instances.
 */
public final class FeasibilityResult {

    private final FeasibilityStatus status;
    private final FeasibilityReason reason;
    private final String explanation;

    // Computed planning values — present for FEASIBLE results, empty for INFEASIBLE
    private final double emptyDriveDistanceKm;
    private final double emptyDriveEnergyKwh;
    private final double tripEnergyKwh;
    private final double socAfterEmptyDrive;
    private final double socAfterTrip;
    private final Duration estimatedEmptyDriveTime;
    private final Duration deadlineSlack;

    private FeasibilityResult(
            FeasibilityStatus status, FeasibilityReason reason, String explanation,
            double emptyDriveDistanceKm, double emptyDriveEnergyKwh, double tripEnergyKwh,
            double socAfterEmptyDrive, double socAfterTrip,
            Duration estimatedEmptyDriveTime, Duration deadlineSlack) {
        this.status = Objects.requireNonNull(status);
        this.reason = Objects.requireNonNull(reason);
        this.explanation = Objects.requireNonNull(explanation);
        this.emptyDriveDistanceKm = emptyDriveDistanceKm;
        this.emptyDriveEnergyKwh = emptyDriveEnergyKwh;
        this.tripEnergyKwh = tripEnergyKwh;
        this.socAfterEmptyDrive = socAfterEmptyDrive;
        this.socAfterTrip = socAfterTrip;
        this.estimatedEmptyDriveTime = estimatedEmptyDriveTime;
        this.deadlineSlack = deadlineSlack;
    }

    /**
     * Creates a FEASIBLE result with all computed planning values.
     *
     * @param emptyDriveDistanceKm   estimated road distance from vehicle location to trip pickup
     * @param emptyDriveEnergyKwh    energy consumed on the empty drive (kWh)
     * @param tripEnergyKwh          energy consumed on the assigned trip (kWh)
     * @param socAfterEmptyDrive     vehicle SoC after completing the empty drive
     * @param socAfterTrip           vehicle SoC after completing the assigned trip
     * @param estimatedEmptyDriveTime travel time for the empty drive
     * @param deadlineSlack          time remaining between estimated trip completion and deadline;
     *                               positive means slack; zero means exactly at deadline
     */
    public static FeasibilityResult feasible(
            double emptyDriveDistanceKm, double emptyDriveEnergyKwh, double tripEnergyKwh,
            double socAfterEmptyDrive, double socAfterTrip,
            Duration estimatedEmptyDriveTime, Duration deadlineSlack) {
        return new FeasibilityResult(
                FeasibilityStatus.FEASIBLE, FeasibilityReason.NONE,
                "Vehicle can serve this trip",
                emptyDriveDistanceKm, emptyDriveEnergyKwh, tripEnergyKwh,
                socAfterEmptyDrive, socAfterTrip,
                estimatedEmptyDriveTime, deadlineSlack);
    }

    /**
     * Creates an INFEASIBLE result with a specific reason and human-readable explanation.
     *
     * @param reason      stable reason code for the infeasibility
     * @param explanation human-readable explanation of the specific constraint that failed
     */
    public static FeasibilityResult infeasible(FeasibilityReason reason, String explanation) {
        return new FeasibilityResult(
                FeasibilityStatus.INFEASIBLE, reason, explanation,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,
                null, null);
    }

    /** Returns true iff the trip is feasible for this vehicle. */
    public boolean isFeasible() {
        return status == FeasibilityStatus.FEASIBLE;
    }

    public FeasibilityStatus getStatus() { return status; }
    public FeasibilityReason getReason() { return reason; }
    public String getExplanation() { return explanation; }

    /**
     * Estimated road distance from vehicle current location to trip pickup, in km.
     * Returns {@link Optional#empty()} for infeasible results.
     */
    public Optional<Double> getEmptyDriveDistanceKm() {
        return isFeasible() ? Optional.of(emptyDriveDistanceKm) : Optional.empty();
    }

    /**
     * Energy consumed on the empty drive to pickup, in kWh.
     * Returns {@link Optional#empty()} for infeasible results.
     */
    public Optional<Double> getEmptyDriveEnergyKwh() {
        return isFeasible() ? Optional.of(emptyDriveEnergyKwh) : Optional.empty();
    }

    /**
     * Energy consumed on the assigned trip, in kWh.
     * Returns {@link Optional#empty()} for infeasible results.
     */
    public Optional<Double> getTripEnergyKwh() {
        return isFeasible() ? Optional.of(tripEnergyKwh) : Optional.empty();
    }

    /**
     * Vehicle SoC (fraction in [0.0, 1.0]) after completing the empty drive.
     * Returns {@link Optional#empty()} for infeasible results.
     */
    public Optional<Double> getSocAfterEmptyDrive() {
        return isFeasible() ? Optional.of(socAfterEmptyDrive) : Optional.empty();
    }

    /**
     * Vehicle SoC (fraction in [0.0, 1.0]) after completing the assigned trip.
     * Returns {@link Optional#empty()} for infeasible results.
     */
    public Optional<Double> getSocAfterTrip() {
        return isFeasible() ? Optional.of(socAfterTrip) : Optional.empty();
    }

    /**
     * Estimated travel time for the empty drive from vehicle location to pickup.
     * Returns {@link Optional#empty()} for infeasible results.
     */
    public Optional<Duration> getEstimatedEmptyDriveTime() {
        return Optional.ofNullable(estimatedEmptyDriveTime);
    }

    /**
     * Time slack between estimated trip completion and the trip's latest allowed finish time.
     * Positive = slack available; zero = exactly at deadline.
     * Returns {@link Optional#empty()} for infeasible results.
     */
    public Optional<Duration> getDeadlineSlack() {
        return Optional.ofNullable(deadlineSlack);
    }

    @Override
    public String toString() {
        if (isFeasible()) {
            return String.format(
                    "FeasibilityResult[FEASIBLE, emptyDist=%.2fkm, emptyEnergy=%.3fkWh, " +
                    "tripEnergy=%.3fkWh, socAfterEmpty=%.3f, socAfterTrip=%.3f, slack=%s]",
                    emptyDriveDistanceKm, emptyDriveEnergyKwh, tripEnergyKwh,
                    socAfterEmptyDrive, socAfterTrip, deadlineSlack);
        }
        return String.format("FeasibilityResult[INFEASIBLE, reason=%s, explanation=%s]",
                reason, explanation);
    }
}
