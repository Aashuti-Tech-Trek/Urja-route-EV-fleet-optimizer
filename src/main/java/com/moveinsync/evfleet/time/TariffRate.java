package com.moveinsync.evfleet.time;

import com.moveinsync.evfleet.exception.InvalidTariffException;
import com.moveinsync.evfleet.validation.DomainValidator;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Immutable value object representing electricity tariff rate per kilowatt-hour (kWh).
 */
public record TariffRate(BigDecimal ratePerKwh, String currency) {

    public TariffRate {
        DomainValidator.requireNonNull(ratePerKwh, "Tariff rate per kWh", InvalidTariffException::new);
        DomainValidator.requireNonBlank(currency, "Tariff currency", InvalidTariffException::new);
        if (ratePerKwh.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidTariffException("Tariff rate per kWh must be non-negative (>= 0), but was: " + ratePerKwh);
        }
        try {
            Currency.getInstance(currency.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidTariffException("Invalid ISO-4217 currency code: " + currency);
        }
        currency = currency.toUpperCase().trim();
    }

    public static TariffRate of(double rate, String currency) {
        return new TariffRate(BigDecimal.valueOf(rate), currency);
    }

    public static TariffRate of(BigDecimal rate, String currency) {
        return new TariffRate(rate, currency);
    }
}
