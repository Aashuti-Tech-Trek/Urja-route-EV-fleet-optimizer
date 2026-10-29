package com.moveinsync.evfleet.validation;

import com.moveinsync.evfleet.exception.InvalidDomainException;

import java.util.Objects;
import java.util.function.Function;

/**
 * Reusable domain validation utilities enforcing domain constraints and invariants.
 */
public final class DomainValidator {

    private DomainValidator() {
        // Utility class
    }

    public static String requireNonBlank(String value, String fieldName) {
        return requireNonBlank(value, fieldName, InvalidDomainException::new);
    }

    public static String requireNonBlank(String value, String fieldName,
                                        Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        if (value == null || value.trim().isEmpty()) {
            throw exceptionSupplier.apply(fieldName + " must not be null or blank");
        }
        return value.trim();
    }

    public static <T> T requireNonNull(T value, String fieldName) {
        return requireNonNull(value, fieldName, InvalidDomainException::new);
    }

    public static <T> T requireNonNull(T value, String fieldName,
                                      Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        if (value == null) {
            throw exceptionSupplier.apply(fieldName + " must not be null");
        }
        return value;
    }

    public static double requireFinite(double value, String fieldName) {
        return requireFinite(value, fieldName, InvalidDomainException::new);
    }

    public static double requireFinite(double value, String fieldName,
                                      Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw exceptionSupplier.apply(fieldName + " must be a finite number, but was: " + value);
        }
        return value;
    }

    public static double requirePositive(double value, String fieldName) {
        return requirePositive(value, fieldName, InvalidDomainException::new);
    }

    public static double requirePositive(double value, String fieldName,
                                        Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        requireFinite(value, fieldName, exceptionSupplier);
        if (value <= 0.0) {
            throw exceptionSupplier.apply(fieldName + " must be strictly positive (> 0), but was: " + value);
        }
        return value;
    }

    public static int requirePositive(int value, String fieldName) {
        return requirePositive(value, fieldName, InvalidDomainException::new);
    }

    public static int requirePositive(int value, String fieldName,
                                     Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        if (value <= 0) {
            throw exceptionSupplier.apply(fieldName + " must be strictly positive (> 0), but was: " + value);
        }
        return value;
    }

    public static double requireNonNegative(double value, String fieldName) {
        return requireNonNegative(value, fieldName, InvalidDomainException::new);
    }

    public static double requireNonNegative(double value, String fieldName,
                                           Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        requireFinite(value, fieldName, exceptionSupplier);
        if (value < 0.0) {
            throw exceptionSupplier.apply(fieldName + " must be non-negative (>= 0), but was: " + value);
        }
        return value;
    }

    public static double requireInRange(double value, double min, double max, String fieldName) {
        return requireInRange(value, min, max, fieldName, InvalidDomainException::new);
    }

    public static double requireInRange(double value, double min, double max, String fieldName,
                                       Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        requireFinite(value, fieldName, exceptionSupplier);
        if (value < min || value > max) {
            throw exceptionSupplier.apply(fieldName + " must be in range [" + min + ", " + max + "], but was: " + value);
        }
        return value;
    }

    public static void requireTrue(boolean condition, String message) {
        requireTrue(condition, message, InvalidDomainException::new);
    }

    public static void requireTrue(boolean condition, String message,
                                   Function<String, ? extends InvalidDomainException> exceptionSupplier) {
        if (!condition) {
            throw exceptionSupplier.apply(message);
        }
    }
}
