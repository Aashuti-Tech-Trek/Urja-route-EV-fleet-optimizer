package com.moveinsync.evfleet.planner;

/**
 * Configuration options for bounded local-search optimization.
 */
public final class LocalSearchConfig {

    private final boolean enabled;
    private final int maxIterations;
    private final long timeoutMillis;

    public LocalSearchConfig(boolean enabled, int maxIterations, long timeoutMillis) {
        this.enabled = enabled;
        this.maxIterations = Math.max(0, maxIterations);
        this.timeoutMillis = Math.max(0, timeoutMillis);
    }

    public static LocalSearchConfig disabled() {
        return new LocalSearchConfig(false, 0, 0);
    }

    public static LocalSearchConfig of(int maxIterations, long timeoutMillis) {
        return new LocalSearchConfig(true, maxIterations, timeoutMillis);
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxIterations() { return maxIterations; }
    public long getTimeoutMillis() { return timeoutMillis; }
}
