package com.wafgateway.worklet1.regex;

/**
 * Thrown when a regular expression pattern has a syntax error.
 *
 * <p>Includes the error message and the position in the pattern where the error
 * was detected, allowing callers to provide precise error diagnostics.
 *
 * @author Worklet 1 Team
 */
public class RegexSyntaxException extends Exception {
    private final int position;

    /**
     * Creates a RegexSyntaxException with a message and position.
     *
     * @param message   the error description
     * @param position  the 0-based character position in the pattern where the error occurred
     */
    public RegexSyntaxException(String message, int position) {
        super(message);
        this.position = position;
    }

    /**
     * Returns the position in the pattern where the error occurred.
     *
     * @return 0-based character position
     */
    public int getPosition() {
        return position;
    }
}
