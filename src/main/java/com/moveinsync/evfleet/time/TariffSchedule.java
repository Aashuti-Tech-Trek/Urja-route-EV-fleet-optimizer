package com.moveinsync.evfleet.time;

import com.moveinsync.evfleet.exception.InvalidTariffException;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tariff schedule managing electricity tariffs for a charging hub or tenant.
 *
 * <p>Slots are defined in terms of local daily time in a specific {@link ZoneId}.
 * Midnight-crossing windows and Daylight Saving Time (DST) changes are fully handled
 * via timezone-aware instant projection.
 *
 * <p>Half-open intervals [start, end) are used throughout. Intermediate energy distributions
 * are maintained at double precision without premature rounding, with the final monetary
 * cost returned as {@link BigDecimal} rounded to 4 decimal places.
 */
public class TariffSchedule {

    private final ZoneId zoneId;
    private final String currency;
    private final List<TariffSlot> slots;
    private final TariffRate defaultRate;

    public TariffSchedule(ZoneId zoneId, String currency, List<TariffSlot> slots, TariffRate defaultRate) {
        this.zoneId = DomainValidator.requireNonNull(zoneId, "Tariff ZoneId", InvalidTariffException::new);
        this.currency = DomainValidator.requireNonBlank(currency, "Tariff currency", InvalidTariffException::new)
                .toUpperCase();
        this.defaultRate = DomainValidator.requireNonNull(defaultRate, "Default tariff rate",
                InvalidTariffException::new);

        if (!this.defaultRate.currency().equalsIgnoreCase(this.currency)) {
            throw new InvalidTariffException(String.format(
                    "Default rate currency '%s' does not match schedule currency '%s'",
                    this.defaultRate.currency(), this.currency));
        }

        List<TariffSlot> validatedSlots = new ArrayList<>();
        if (slots != null) {
            for (TariffSlot slot : slots) {
                DomainValidator.requireNonNull(slot, "Tariff slot", InvalidTariffException::new);
                if (!slot.rate().currency().equalsIgnoreCase(this.currency)) {
                    throw new InvalidTariffException(String.format(
                            "Slot rate currency '%s' does not match schedule currency '%s'",
                            slot.rate().currency(), this.currency));
                }
                validatedSlots.add(slot);
            }
        }
        this.slots = Collections.unmodifiableList(validatedSlots);
    }

    public static TariffSchedule ofFlatRate(ZoneId zoneId, TariffRate flatRate) {
        DomainValidator.requireNonNull(flatRate, "Flat rate", InvalidTariffException::new);
        TariffSlot allDaySlot = new TariffSlot(LocalTime.MIN, LocalTime.MIN, flatRate, TariffType.STANDARD);
        return new TariffSchedule(zoneId, flatRate.currency(), List.of(allDaySlot), flatRate);
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public String getCurrency() {
        return currency;
    }

    public List<TariffSlot> getSlots() {
        return slots;
    }

    public TariffRate getDefaultRate() {
        return defaultRate;
    }

    /**
     * Resolves the active tariff rate at a specific instant according to the local timezone.
     *
     * @param instant the instant to check
     * @return active TariffRate (or default rate if no matching slot)
     */
    public TariffRate getRateAt(Instant instant) {
        DomainValidator.requireNonNull(instant, "Instant", InvalidTariffException::new);
        LocalTime localTime = instant.atZone(zoneId).toLocalTime();

        for (TariffSlot slot : slots) {
            if (slot.matches(localTime)) {
                return slot.rate();
            }
        }
        return defaultRate;
    }

    /**
     * Calculates electricity cost for energy delivered over the time interval [start, end).
     *
     * <p>If the interval spans multiple tariff slots (e.g. crossing peak to off-peak or midnight),
     * energy is allocated proportionally to time duration across slots.
     *
     * @param start     start instant (inclusive)
     * @param end       end instant (exclusive)
     * @param energyKwh total energy in kWh delivered over the interval
     * @return calculated monetary cost as BigDecimal with scale 4
     */
    public BigDecimal calculateCost(Instant start, Instant end, double energyKwh) {
        DomainValidator.requireNonNull(start, "Start instant", InvalidTariffException::new);
        DomainValidator.requireNonNull(end, "End instant", InvalidTariffException::new);
        DomainValidator.requireNonNegative(energyKwh, "Energy in kWh", InvalidTariffException::new);

        if (end.isBefore(start)) {
            throw new InvalidTariffException("End instant (" + end + ") cannot be before start instant (" + start + ")");
        }

        if (start.equals(end) || energyKwh == 0.0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        long totalDurationMillis = Duration.between(start, end).toMillis();
        if (totalDurationMillis <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        Instant current = start;

        // Step through intervals as tariff boundaries are reached
        while (current.isBefore(end)) {
            ZonedDateTime zdt = current.atZone(zoneId);
            LocalTime localTime = zdt.toLocalTime();
            LocalDate localDate = zdt.toLocalDate();

            TariffSlot activeSlot = findActiveSlot(localTime);
            TariffRate activeRate = (activeSlot != null) ? activeSlot.rate() : defaultRate;

            Instant nextBoundary = findNextBoundary(current, zdt, localDate, activeSlot, end);
            if (!nextBoundary.isAfter(current)) {
                // Safeguard against zero-progress edge case in DST fall-back or boundary rounding
                nextBoundary = current.plusSeconds(60);
                if (nextBoundary.isAfter(end)) {
                    nextBoundary = end;
                }
            }

            long chunkDurationMillis = Duration.between(current, nextBoundary).toMillis();
            double chunkEnergyFraction = (double) chunkDurationMillis / (double) totalDurationMillis;
            double chunkEnergyKwh = energyKwh * chunkEnergyFraction;

            BigDecimal chunkCost = BigDecimal.valueOf(chunkEnergyKwh)
                    .multiply(activeRate.ratePerKwh());
            totalCost = totalCost.add(chunkCost);

            current = nextBoundary;
        }

        return totalCost.setScale(4, RoundingMode.HALF_UP);
    }

    private TariffSlot findActiveSlot(LocalTime localTime) {
        for (TariffSlot slot : slots) {
            if (slot.matches(localTime)) {
                return slot;
            }
        }
        return null;
    }

    private Instant findNextBoundary(Instant current, ZonedDateTime zdt, LocalDate localDate,
                                     TariffSlot activeSlot, Instant end) {
        Instant earliestBoundary = end;

        if (activeSlot != null && !activeSlot.isAllDay()) {
            LocalTime slotEnd = activeSlot.endTime();
            LocalDate boundaryDate = localDate;
            if (activeSlot.spansMidnight() && !zdt.toLocalTime().isBefore(activeSlot.startTime())) {
                boundaryDate = localDate.plusDays(1);
            }
            ZonedDateTime boundaryZdt = boundaryDate.atTime(slotEnd).atZone(zoneId);
            Instant boundaryInstant = boundaryZdt.toInstant();
            if (boundaryInstant.isAfter(current) && boundaryInstant.isBefore(earliestBoundary)) {
                earliestBoundary = boundaryInstant;
            }
        }

        // Also check if any other slot starts before earliestBoundary
        for (TariffSlot slot : slots) {
            if (slot.isAllDay()) {
                continue;
            }
            LocalTime slotStart = slot.startTime();
            ZonedDateTime startZdtToday = localDate.atTime(slotStart).atZone(zoneId);
            Instant startInstantToday = startZdtToday.toInstant();
            if (startInstantToday.isAfter(current) && startInstantToday.isBefore(earliestBoundary)) {
                earliestBoundary = startInstantToday;
            }

            ZonedDateTime startZdtTomorrow = localDate.plusDays(1).atTime(slotStart).atZone(zoneId);
            Instant startInstantTomorrow = startZdtTomorrow.toInstant();
            if (startInstantTomorrow.isAfter(current) && startInstantTomorrow.isBefore(earliestBoundary)) {
                earliestBoundary = startInstantTomorrow;
            }
        }

        return earliestBoundary;
    }
}
