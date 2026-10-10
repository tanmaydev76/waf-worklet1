package com.wafgateway.worklet1;

/**
 * Thrown when {@code worklet1.properties} (or an equivalent config source)
 * can't be loaded or contains an invalid value.
 *
 * @author Worklet 1 Team
 */
public class ConfigLoadException extends Exception {

    /**
     * Creates the exception with an explanatory message.
     *
     * @param message human-readable explanation
     */
    public ConfigLoadException(String message) {
        super(message);
    }

    /**
     * Creates the exception with an explanatory message and an underlying cause.
     *
     * @param message human-readable explanation
     * @param cause   the lower-level failure
     */
    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
