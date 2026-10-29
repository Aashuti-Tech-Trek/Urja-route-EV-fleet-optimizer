package com.moveinsync.evfleet.validator;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable report produced by the independent plan validator summarizing the hard-constraint evaluation.
 */
public final class ValidationReport {

    private final ValidationStatus status;
    private final int totalChecksExecuted;
    private final int violationCount;
    private final int incompleteChecks;
    private final List<PlanViolation> violations;
    private final Duration validationDuration;

    public ValidationReport(ValidationStatus status,
                            int totalChecksExecuted,
                            int incompleteChecks,
                            List<PlanViolation> violations,
                            Duration validationDuration) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.totalChecksExecuted = totalChecksExecuted;
        this.incompleteChecks = incompleteChecks;
        this.violations = violations != null ? List.copyOf(violations) : Collections.emptyList();
        this.violationCount = this.violations.size();
        this.validationDuration = validationDuration != null ? validationDuration : Duration.ZERO;
    }

    public static ValidationReport valid(int totalChecksExecuted, Duration duration) {
        return new ValidationReport(ValidationStatus.VALID, totalChecksExecuted, 0, List.of(), duration);
    }

    public static ValidationReport invalid(int totalChecksExecuted, List<PlanViolation> violations, Duration duration) {
        return new ValidationReport(ValidationStatus.INVALID, totalChecksExecuted, 0, violations, duration);
    }

    public static ValidationReport indeterminate(int totalChecksExecuted, int incompleteChecks,
                                                 List<PlanViolation> violations, Duration duration) {
        return new ValidationReport(ValidationStatus.INDETERMINATE, totalChecksExecuted, incompleteChecks, violations, duration);
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public boolean isValid() {
        return status == ValidationStatus.VALID;
    }

    public boolean isInvalid() {
        return status == ValidationStatus.INVALID;
    }

    public boolean isIndeterminate() {
        return status == ValidationStatus.INDETERMINATE;
    }

    public int getTotalChecksExecuted() {
        return totalChecksExecuted;
    }

    public int getViolationCount() {
        return violationCount;
    }

    public int getIncompleteChecks() {
        return incompleteChecks;
    }

    public List<PlanViolation> getViolations() {
        return violations;
    }

    public Duration getValidationDuration() {
        return validationDuration;
    }

    public List<PlanViolation> getViolationsByCode(ViolationCode code) {
        if (code == null) return Collections.emptyList();
        return violations.stream()
                .filter(v -> v.getCode() == code)
                .collect(Collectors.toList());
    }

    public List<PlanViolation> getViolationsForEntity(String entityId) {
        if (entityId == null) return Collections.emptyList();
        return violations.stream()
                .filter(v -> entityId.equalsIgnoreCase(v.getEntityId()))
                .collect(Collectors.toList());
    }

    public String toDetailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("ValidationReport [Status: %s, Checks: %d, Violations: %d, Incomplete: %d, Time: %d ms]\n",
                status, totalChecksExecuted, violationCount, incompleteChecks, validationDuration.toMillis()));
        for (PlanViolation v : violations) {
            sb.append("  - ").append(v).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("ValidationReport[status=%s, checks=%d, violations=%d, duration=%dms]",
                status, totalChecksExecuted, violationCount, validationDuration.toMillis());
    }
}
