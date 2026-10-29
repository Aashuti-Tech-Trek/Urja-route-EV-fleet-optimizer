package com.moveinsync.evfleet.replan;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Event representing an unexpected physical charger hub outage or failure.
 */
public final class ChargerFailureEvent {

    private final String eventId;
    private final String hubId;
    private final Instant failureTimestamp;
    private final Instant expectedRecoveryTimestamp; // nullable if indefinite

    public ChargerFailureEvent(String eventId, String hubId, Instant failureTimestamp, Instant expectedRecoveryTimestamp) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.hubId = Objects.requireNonNull(hubId, "hubId must not be null");
        this.failureTimestamp = Objects.requireNonNull(failureTimestamp, "failureTimestamp must not be null");
        this.expectedRecoveryTimestamp = expectedRecoveryTimestamp;

        if (expectedRecoveryTimestamp != null && !expectedRecoveryTimestamp.isAfter(failureTimestamp)) {
            throw new IllegalArgumentException(String.format(
                    "Recovery timestamp (%s) must be strictly after failure timestamp (%s)",
                    expectedRecoveryTimestamp, failureTimestamp));
        }
    }

    public String getEventId() {
        return eventId;
    }

    public String getHubId() {
        return hubId;
    }

    public Instant getFailureTimestamp() {
        return failureTimestamp;
    }

    public Optional<Instant> getExpectedRecoveryTimestamp() {
        return Optional.ofNullable(expectedRecoveryTimestamp);
    }

    public boolean isOutageDuring(Instant start, Instant end) {
        // Outage interval is [failureTimestamp, recoveryTimestamp)
        if (expectedRecoveryTimestamp == null) {
            // Indefinite outage starting at failureTimestamp
            return !end.isBefore(failureTimestamp);
        }
        return start.isBefore(expectedRecoveryTimestamp) && end.isAfter(failureTimestamp);
    }

    @Override
    public String toString() {
        return String.format("ChargerFailureEvent[id=%s, hub=%s, failedAt=%s, recoveryAt=%s]",
                eventId, hubId, failureTimestamp, expectedRecoveryTimestamp);
    }
}
