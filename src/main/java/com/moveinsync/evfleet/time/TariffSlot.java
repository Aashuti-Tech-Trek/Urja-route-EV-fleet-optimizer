package com.moveinsync.evfleet.time;

import com.moveinsync.evfleet.exception.InvalidTariffException;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.time.LocalTime;

/**
 * Represents a recurring daily time slot associated with a specific electricity tariff rate.
 *
 * <p>Supports windows that cross midnight (e.g., 22:00 to 06:00 for overnight off-peak charging).
 * Intervals are evaluated with half-open semantics: [startTime, endTime).
 */
public record TariffSlot(LocalTime startTime, LocalTime endTime, TariffRate rate, TariffType type) {

    public TariffSlot {
        DomainValidator.requireNonNull(startTime, "Slot start time", InvalidTariffException::new);
        DomainValidator.requireNonNull(endTime, "Slot end time", InvalidTariffException::new);
        DomainValidator.requireNonNull(rate, "Slot tariff rate", InvalidTariffException::new);
        DomainValidator.requireNonNull(type, "Slot tariff type", InvalidTariffException::new);
    }

    /**
     * Checks if this tariff slot spans across midnight (e.g., 22:00 to 06:00).
     */
    public boolean spansMidnight() {
        return endTime.isBefore(startTime);
    }

    /**
     * Checks if this slot covers the entire 24-hour day (e.g., startTime == endTime).
     */
    public boolean isAllDay() {
        return startTime.equals(endTime);
    }

    /**
     * Checks whether the given local time falls into this tariff slot.
     * Evaluated as [startTime, endTime) half-open interval.
     *
     * @param time local time to check, not null
     * @return true if time matches this slot
     */
    public boolean matches(LocalTime time) {
        DomainValidator.requireNonNull(time, "Local time", InvalidTariffException::new);
        if (isAllDay()) {
            return true;
        }
        if (spansMidnight()) {
            // e.g. 22:00 to 06:00 matches if time >= 22:00 OR time < 06:00
            return !time.isBefore(startTime) || time.isBefore(endTime);
        } else {
            // standard slot, e.g. 06:00 to 18:00 matches if time >= 06:00 AND time < 18:00
            return !time.isBefore(startTime) && time.isBefore(endTime);
        }
    }
}
