package com.wafgateway.worklet1.session;

import com.wafgateway.worklet1.automata.AutomataCompiler;
import com.wafgateway.worklet1.automata.Dfa;
import com.wafgateway.worklet1.automata.MatchMode;
import com.wafgateway.worklet1.automata.StateLimitExceededException;
import com.wafgateway.worklet1.regex.RegexSyntaxException;

import java.nio.charset.StandardCharsets;

/**
 * Validates that a session id has the expected shape: exactly 32 lowercase
 * hexadecimal characters (e.g. an MD5-style hex digest).
 *
 * <p>Worklet 1 only checks the *format* here — it does not create, store, or
 * expire sessions. A missing session cookie is not an error at all (the
 * caller treats the session id as absent); a present-but-malformed value is
 * what this class exists to catch, so the request can be blocked with
 * {@code BAD_SESSION_ID} before it ever reaches Worklet 3 (the Turing-machine
 * layer that actually tracks session/workflow state).
 *
 * <p>Uses a compiled {@code FULL_MATCH} DFA rather than hand-written
 * character checks for the same reason as everywhere else in this project:
 * guaranteed O(n) validation with no backtracking, consistent with how every
 * other pattern in the system is matched.
 *
 * @author Worklet 1 Team
 */
public final class SessionIdValidator {

    /** Exactly 32 lowercase hex characters — the only shape this project trusts. */
    private static final String PATTERN = "[0-9a-f]{32}";

    private final Dfa dfa;

    /**
     * Builds the validator's DFA.
     *
     * @param maxStates the DFA state limit (see {@link AutomataCompiler})
     * @throws RegexSyntaxException        if the pattern is malformed (should
     *                                      never happen for the fixed pattern above)
     * @throws StateLimitExceededException if the DFA needs too many states
     */
    public SessionIdValidator(int maxStates) throws RegexSyntaxException, StateLimitExceededException {
        AutomataCompiler compiler = new AutomataCompiler(maxStates);
        this.dfa = compiler.compile(PATTERN, MatchMode.FULL_MATCH);
    }

    /**
     * Checks whether a session id is exactly 32 lowercase hex characters.
     *
     * @param sessionId the candidate value (e.g. a cookie's raw value); null
     *                   is treated as invalid rather than throwing
     * @return true if and only if the value matches {@code [0-9a-f]{32}} exactly
     */
    public boolean isValid(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return dfa.fullMatch(sessionId.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the DFA's state count, for diagnostics/reporting.
     *
     * @return the state count
     */
    public int stateCount() {
        return dfa.stateCount();
    }
}
