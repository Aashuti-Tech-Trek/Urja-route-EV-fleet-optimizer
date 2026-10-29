package com.moveinsync.evfleet.replan;

public enum ReplanningOutcome {
    SUCCESS,
    NO_CHANGE_REQUIRED,
    PARTIALLY_RECOVERED,
    INFEASIBLE,
    TIME_BUDGET_EXCEEDED,
    STALE_EVENT,
    INVALID_PLAN
}
