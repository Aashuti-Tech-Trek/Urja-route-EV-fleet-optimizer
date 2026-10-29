package com.moveinsync.evfleet.planner;

import com.moveinsync.evfleet.validator.DefaultPlanValidator;
import com.moveinsync.evfleet.validator.PlanValidator;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validator adapter delegating to {@link DefaultPlanValidator} to ensure backwards compatibility
 * across all existing planner and test integrations.
 */
public final class FleetPlanValidator {

    private final PlanValidator delegate;

    public FleetPlanValidator() {
        this(new DefaultPlanValidator());
    }

    public FleetPlanValidator(PlanValidator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    public static final class ValidationReport {
        private final boolean valid;
        private final List<String> violations;

        public ValidationReport(boolean valid, List<String> violations) {
            this.valid = valid;
            this.violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        }

        public boolean isValid() { return valid; }
        public List<String> getViolations() { return violations; }

        @Override
        public String toString() {
            return valid ? "ValidationReport[VALID]" : "ValidationReport[INVALID: " + violations + "]";
        }
    }

    /**
     * Validates a plan against the input scenario.
     *
     * @param result the completed plan result
     * @param input the input planning scenario
     * @return ValidationReport with detailed violation messages if invalid
     */
    public ValidationReport validate(FleetPlanResult result, PlanningInput input) {
        com.moveinsync.evfleet.validator.ValidationReport report = delegate.validate(result, input);
        List<String> messages = report.getViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.toList());
        return new ValidationReport(report.isValid(), messages);
    }

    /**
     * Returns the full structured validation report with machine-readable violation codes.
     */
    public com.moveinsync.evfleet.validator.ValidationReport validateDetailed(FleetPlanResult result, PlanningInput input) {
        return delegate.validate(result, input);
    }
}
