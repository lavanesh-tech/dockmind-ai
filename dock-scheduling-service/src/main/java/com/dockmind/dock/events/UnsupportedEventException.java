package com.dockmind.dock.events;

/**
 * The event can never be processed (malformed, unknown version, missing fields). Retrying
 * won't help, so the error handler sends it straight to the dead-letter topic.
 */
public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String message) {
        super(message);
    }
}
