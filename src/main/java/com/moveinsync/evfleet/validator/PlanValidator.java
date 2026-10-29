package com.moveinsync.evfleet.validator;

import com.moveinsync.evfleet.planner.FleetPlanResult;
import com.moveinsync.evfleet.planner.PlanningInput;

/**
 * Independent verification interface that evaluates candidate fleet plans against mandatory
 * physical, temporal, operational, and financial hard constraints.
 *
 * <p>Contract:
 * <ul>
 *   <li>Must not trust planner's internal feasibility flags or objective scores.</li>
 *   <li>Must not mutate either the input or candidate plan.</li>
 *   <li>Must return all discovered violations rather than failing fast on the first error.</li>
 * </ul>
 */
public interface PlanValidator {

    /**
     * Independently validates a candidate fleet plan against the original planning input scenario.
     *
     * @param plan the candidate fleet plan result to verify
     * @param input the authoritative planning input scenario
     * @return structured, immutable validation report with detailed violations and status
     */
    ValidationReport validate(FleetPlanResult plan, PlanningInput input);
}
