package com.moveinsync.evfleet.time;

import com.moveinsync.evfleet.exception.InvalidDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeWindowTest {

    private final Instant t1 = Instant.parse("2026-10-29T10:00:00Z");
    private final Instant t2 = Instant.parse("2026-10-29T11:00:00Z");
    private final Instant t3 = Instant.parse("2026-10-29T12:00:00Z");
    private final Instant t4 = Instant.parse("2026-10-29T13:00:00Z");

    @Test
    @DisplayName("Valid start and end creates TimeWindow")
    void shouldCreateValidTimeWindow() {
        TimeWindow window = new TimeWindow(t1, t2);
        assertThat(window.start()).isEqualTo(t1);
        assertThat(window.end()).isEqualTo(t2);
        assertThat(window.duration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("Reject invalid time window where end is before or equal to start")
    void shouldRejectInvalidIntervals() {
        assertThatThrownBy(() -> new TimeWindow(t2, t1))
                .isInstanceOf(InvalidDomainException.class)
                .hasMessageContaining("strictly after");

        assertThatThrownBy(() -> new TimeWindow(t1, t1))
                .isInstanceOf(InvalidDomainException.class)
                .hasMessageContaining("strictly after");
    }

    @Test
    @DisplayName("Adjacent intervals [T1, T2) and [T2, T3) must NOT count as overlapping")
    void adjacentIntervalsMustNotOverlap() {
        TimeWindow first = new TimeWindow(t1, t2);
        TimeWindow second = new TimeWindow(t2, t3);

        assertThat(first.overlaps(second)).isFalse();
        assertThat(second.overlaps(first)).isFalse();
        assertThat(first.isAdjacentTo(second)).isTrue();
        assertThat(second.isAdjacentTo(first)).isTrue();
    }

    @Test
    @DisplayName("Overlapping intervals are detected in both directions")
    void overlappingIntervalsAreDetected() {
        TimeWindow windowA = new TimeWindow(t1, t3); // 10:00 - 12:00
        TimeWindow windowB = new TimeWindow(t2, t4); // 11:00 - 13:00

        assertThat(windowA.overlaps(windowB)).isTrue();
        assertThat(windowB.overlaps(windowA)).isTrue();

        Optional<TimeWindow> intersection = windowA.intersection(windowB);
        assertThat(intersection).isPresent();
        assertThat(intersection.get().start()).isEqualTo(t2);
        assertThat(intersection.get().end()).isEqualTo(t3);
    }

    @Test
    @DisplayName("Disjoint intervals do not overlap and have empty intersection")
    void disjointIntervalsDoNotOverlap() {
        TimeWindow first = new TimeWindow(t1, t2); // 10:00 - 11:00
        TimeWindow second = new TimeWindow(t3, t4); // 12:00 - 13:00

        assertThat(first.overlaps(second)).isFalse();
        assertThat(first.isAdjacentTo(second)).isFalse();
        assertThat(first.intersection(second)).isEmpty();
    }

    @Test
    @DisplayName("Half-open semantics [start, end): contains start, excludes end")
    void halfOpenIntervalContainsStartAndExcludesEnd() {
        TimeWindow window = new TimeWindow(t1, t2);

        assertThat(window.contains(t1)).isTrue();
        assertThat(window.contains(t1.plusMillis(1))).isTrue();
        assertThat(window.contains(t2.minusMillis(1))).isTrue();
        assertThat(window.contains(t2)).isFalse();
    }
}
