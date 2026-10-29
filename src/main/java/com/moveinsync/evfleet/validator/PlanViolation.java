package com.moveinsync.evfleet.validator;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a specific hard-constraint violation or accounting discrepancy detected in a fleet plan.
 */
public final class PlanViolation {

    private final ViolationCode code;
    private final String entityId;
    private final Instant eventTime;
    private final String measuredValue;
    private final String expectedValue;
    private final String message;

    public PlanViolation(ViolationCode code, String entityId, Instant eventTime,
                         String measuredValue, String expectedValue, String message) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.entityId = entityId != null ? entityId : "GLOBAL";
        this.eventTime = eventTime;
        this.measuredValue = measuredValue != null ? measuredValue : "N/A";
        this.expectedValue = expectedValue != null ? expectedValue : "N/A";
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    public static PlanViolation of(ViolationCode code, String entityId, String message) {
        return new PlanViolation(code, entityId, null, null, null, message);
    }

    public static PlanViolation of(ViolationCode code, String entityId, Instant eventTime,
                                   String measuredValue, String expectedValue, String message) {
        return new PlanViolation(code, entityId, eventTime, measuredValue, expectedValue, message);
    }

    public ViolationCode getCode() {
        return code;
    }

    public String getEntityId() {
        return entityId;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public String getMeasuredValue() {
        return measuredValue;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanViolation that)) return false;
        return code == that.code &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(eventTime, that.eventTime) &&
                Objects.equals(measuredValue, that.measuredValue) &&
                Objects.equals(expectedValue, that.expectedValue) &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, entityId, eventTime, measuredValue, expectedValue, message);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(code).append("] Entity: ").append(entityId);
        if (eventTime != null) {
            sb.append(" at ").append(eventTime);
        }
        if (!"N/A".equals(measuredValue) || !"N/A".equals(expectedValue)) {
            sb.append(" (measured: ").append(measuredValue).append(", expected: ").append(expectedValue).append(")");
        }
        sb.append(" - ").append(message);
        return sb.toString();
    }
}
