package com.wafgateway.worklet1.automata;

/**
 * How a compiled {@link Dfa} should be matched against input.
 *
 * @author Worklet 1 Team
 */
public enum MatchMode {
    /**
     * The input must match the pattern in its entirety (or, for
     * {@link Dfa#longestMatch}, the longest accepted prefix is reported).
     * Used for tokenizing and session-id validation, where the caller already
     * knows where the candidate text starts and ends.
     */
    FULL_MATCH,

    /**
     * The pattern may match anywhere within the input, not just as a full
     * match. Implemented by unioning ε-closure(start) into every computed
     * next-state-set during subset construction — equivalent to prefixing the
     * original pattern with {@code .*}. Used for attack-signature scanning,
     * where a rule like {@code <script} must be found no matter where it
     * appears in a request field.
     */
    SEARCH
}
