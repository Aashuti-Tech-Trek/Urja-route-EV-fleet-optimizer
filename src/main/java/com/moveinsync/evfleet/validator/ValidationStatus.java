package com.moveinsync.evfleet.validator;

/**
 * High-level status of an independent plan validation execution.
 */
public enum ValidationStatus {

    /**
     * All mandatory hard constraints, energy accounting, and concurrency invariants were verified clean.
     */
    VALID,

    /**
     * One or more mandatory hard constraints or invariants were violated.
     */
    INVALID,

    /**
     * Required validation data was missing or corrupted, preventing full invariant verification.
     */
    INDETERMINATE
}
