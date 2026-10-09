package com.wafgateway.worklet1.automata;

/**
 * Thrown when subset construction would need more DFA states than the
 * configured limit allows.
 *
 * <p>A DFA's state count can, in the worst case, grow exponentially relative
 * to the NFA it was built from (this is the one place in the whole pipeline
 * where that risk exists — Thompson's construction is always linear, and
 * Hopcroft minimization never increases state count). Bounding it here turns
 * a pathological pattern into a clear startup failure instead of unbounded
 * memory use.
 *
 * @author Worklet 1 Team
 */
public class StateLimitExceededException extends Exception {

    /**
     * Creates the exception with a message explaining what was exceeded.
     *
     * @param message human-readable explanation, including the configured limit
     */
    public StateLimitExceededException(String message) {
        super(message);
    }
}
