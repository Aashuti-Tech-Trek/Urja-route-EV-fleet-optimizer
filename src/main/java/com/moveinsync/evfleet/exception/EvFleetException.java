package com.moveinsync.evfleet.exception;

/**
 * Base unchecked exception for all MoveInSync EV Fleet domain errors.
 */
public class EvFleetException extends RuntimeException {

    public EvFleetException(String message) {
        super(message);
    }

    public EvFleetException(String message, Throwable cause) {
        super(message, cause);
    }
}
