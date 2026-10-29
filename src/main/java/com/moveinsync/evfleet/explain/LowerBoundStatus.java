package com.moveinsync.evfleet.explain;

/** Describes the reliability of a computed lower bound. */
public enum LowerBoundStatus {
    /** Bound is mathematically sound and useful under stated assumptions. */
    AVAILABLE,
    /** Bound is valid but trivial (e.g., zero), providing limited information. */
    WEAK,
    /** Bound cannot be calculated reliably; reason is stated in the result. */
    UNAVAILABLE
}
