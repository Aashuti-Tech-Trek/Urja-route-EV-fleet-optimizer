package com.moveinsync.evfleet.time;

import com.moveinsync.evfleet.exception.InvalidDomainException;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a time interval with half-open semantics: [start, end).
 * The start instant is inclusive and the end instant is exclusive.
 *
 * <p>Key invariant: start must be strictly before end.
 * Adjacent intervals [T1, T2) and [T2, T3) do not overlap.
 */
public record TimeWindow(Instant start, Instant end) {

    public TimeWindow {
        DomainValidator.requireNonNull(start, "Start instant");
        DomainValidator.requireNonNull(end, "End instant");
        DomainValidator.requireTrue(end.isAfter(start),
                "TimeWindow end (" + end + ") must be strictly after start (" + start + ")");
    }

    /**
     * Checks if this window overlaps with another window using half-open semantics.
     * Adjacent windows (e.g. [10:00, 11:00) and [11:00, 12:00)) do NOT overlap.
     *
     * @param other the other time window
     * @return true if there is non-zero duration overlap
     */
    public boolean overlaps(TimeWindow other) {
        DomainValidator.requireNonNull(other, "Other time window");
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    /**
     * Checks whether an instant falls within this half-open interval: [start, end).
     *
     * @param instant the instant to test
     * @return true if start &lt;= instant &lt; end
     */
    public boolean contains(Instant instant) {
        DomainValidator.requireNonNull(instant, "Instant");
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    /**
     * Checks if this window is immediately adjacent to another window without gap or overlap.
     *
     * @param other the other time window
     * @return true if one window ends exactly when the other begins
     */
    public boolean isAdjacentTo(TimeWindow other) {
        DomainValidator.requireNonNull(other, "Other time window");
        return this.end.equals(other.start) || this.start.equals(other.end);
    }

    /**
     * Returns the overlapping intersection of two windows, if any.
     *
     * @param other the other time window
     * @return Optional containing the overlapping TimeWindow, or empty if no overlap
     */
    public Optional<TimeWindow> intersection(TimeWindow other) {
        DomainValidator.requireNonNull(other, "Other time window");
        if (!overlaps(other)) {
            return Optional.empty();
        }
        Instant latestStart = this.start.isAfter(other.start) ? this.start : other.start;
        Instant earliestEnd = this.end.isBefore(other.end) ? this.end : other.end;
        return Optional.of(new TimeWindow(latestStart, earliestEnd));
    }

    /**
     * Returns the duration of this time window.
     *
     * @return non-zero Duration
     */
    public Duration duration() {
        return Duration.between(start, end);
    }
}
