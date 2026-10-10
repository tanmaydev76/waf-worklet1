package com.wafgateway.worklet1;

import com.wafgateway.common.Token;
import com.wafgateway.worklet1.signature.Match;

import java.util.List;

/**
 * The outcome of inspecting one request.
 *
 * @param blocked   whether the request was blocked
 * @param reason    why it was blocked; null if it passed. Can also be null
 *                  with {@code blocked == true} in the one case {@link
 *                  Worklet1#inspect} catches an entirely unexpected internal
 *                  error rather than one of the six defined reasons — see
 *                  {@code detail} for what happened in that case
 * @param matches   every signature match found (not just the ones that
 *                  crossed the severity threshold); non-empty only when
 *                  {@code reason == SIGNATURE}, empty otherwise (never null)
 * @param detail    a human-readable explanation; null if it passed
 * @param tokens    the JSON body's tokens if it passed and had a JSON body;
 *                  an empty list if it passed with no/non-JSON body, or if
 *                  it was blocked (never null)
 * @param sessionId the valid session id if present and well-formed; null if
 *                  absent, or if the request was blocked
 */
public record Worklet1Result(
        boolean blocked,
        BlockReason reason,
        List<Match> matches,
        String detail,
        List<Token> tokens,
        String sessionId) {

    /**
     * Guarantees {@code matches} and {@code tokens} are never null, even if
     * constructed carelessly — callers can always iterate them directly.
     */
    public Worklet1Result {
        if (matches == null) {
            matches = List.of();
        }
        if (tokens == null) {
            tokens = List.of();
        }
    }
}
