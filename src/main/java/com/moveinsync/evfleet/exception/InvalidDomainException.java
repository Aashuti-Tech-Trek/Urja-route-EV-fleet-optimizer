package com.moveinsync.evfleet.exception;

/**
 * Thrown when domain validation fails or invariant is violated.
 */
public class InvalidDomainException extends EvFleetException {

    public InvalidDomainException(String message) {
        super(message);
    }

    public InvalidDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
