package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.domain.Trip;
import com.moveinsync.evfleet.domain.Vehicle;

import java.util.Objects;

/**
 * An immutable record of one trip assignment — either to an EV or to a backup cab.
 */
public final class TripAssignment {

    public enum AssignmentType { EV, BACKUP_CAB }

    private final Trip trip;
    private final Vehicle assignedVehicle;   // null when assignmentType == BACKUP_CAB
    private final AssignmentType assignmentType;
    private final FeasibilityReason backupReason;  // null when assignmentType == EV
    private final String backupReasonDetail;       // null when assignmentType == EV

    private TripAssignment(Trip trip, Vehicle assignedVehicle, AssignmentType assignmentType,
                           FeasibilityReason backupReason, String backupReasonDetail) {
        this.trip = Objects.requireNonNull(trip, "trip");
        this.assignedVehicle = assignedVehicle;
        this.assignmentType = Objects.requireNonNull(assignmentType, "assignmentType");
        this.backupReason = backupReason;
        this.backupReasonDetail = backupReasonDetail;
    }

    /** Creates an EV assignment. */
    public static TripAssignment toEv(Trip trip, Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle must not be null for EV assignment");
        return new TripAssignment(trip, vehicle, AssignmentType.EV, null, null);
    }

    /**
     * Creates a backup-cab assignment with the last known rejection reason.
     * The reason represents the primary constraint that prevented any EV from serving the trip;
     * it does NOT imply that no EV assignment is mathematically possible — only that the
     * heuristic could not find a feasible EV assignment in this planning run.
     */
    public static TripAssignment toBackup(Trip trip, FeasibilityReason lastRejectionReason,
                                          String lastRejectionDetail) {
        return new TripAssignment(trip, null, AssignmentType.BACKUP_CAB,
                lastRejectionReason != null ? lastRejectionReason : FeasibilityReason.NONE,
                lastRejectionDetail != null ? lastRejectionDetail : "No feasible EV found by heuristic");
    }

    public Trip getTrip() { return trip; }
    public Vehicle getAssignedVehicle() { return assignedVehicle; }
    public AssignmentType getAssignmentType() { return assignmentType; }
    public boolean isEvAssignment() { return assignmentType == AssignmentType.EV; }
    public boolean isBackupAssignment() { return assignmentType == AssignmentType.BACKUP_CAB; }
    public FeasibilityReason getBackupReason() { return backupReason; }
    public String getBackupReasonDetail() { return backupReasonDetail; }

    @Override
    public String toString() {
        if (isEvAssignment()) {
            return String.format("TripAssignment[EV, trip=%s, vehicle=%s]",
                    trip.getTripId(), assignedVehicle.getVehicleId());
        }
        return String.format("TripAssignment[BACKUP, trip=%s, reason=%s]",
                trip.getTripId(), backupReason);
    }
}
