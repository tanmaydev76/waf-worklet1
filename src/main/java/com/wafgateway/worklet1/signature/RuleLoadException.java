package com.wafgateway.worklet1.signature;

/**
 * Thrown when {@code rules.txt} (or an equivalent rules source) can't be
 * loaded: the file is missing, a line is malformed, an id is duplicated, a
 * severity name is unrecognized, or a rule's pattern fails to compile.
 *
 * <p>Always includes enough context (rule id and/or line number) to find and
 * fix the offending line, since this is meant to fail startup loudly rather
 * than silently skip a bad rule.
 *
 * @author Worklet 1 Team
 */
public class RuleLoadException extends Exception {

    /**
     * Creates the exception with an explanatory message.
     *
     * @param message human-readable explanation, including rule id/line context
     */
    public RuleLoadException(String message) {
        super(message);
    }

    /**
     * Creates the exception with an explanatory message and an underlying cause.
     *
     * @param message human-readable explanation, including rule id/line context
     * @param cause   the lower-level failure (e.g. a regex syntax error, an I/O error)
     */
    public RuleLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
