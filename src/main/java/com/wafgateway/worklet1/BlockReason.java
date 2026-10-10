package com.wafgateway.worklet1;

/**
 * Why {@link Worklet1#inspect} blocked a request. {@code null} on a
 * {@link Worklet1Result} means the request passed.
 *
 * @author Worklet 1 Team
 */
public enum BlockReason {
    /** The request body exceeded the configured size limit. */
    BODY_TOO_LARGE,
    /** An attack signature matched at or above the configured severity threshold. */
    SIGNATURE,
    /** The session cookie was present but did not look like a valid session id. */
    BAD_SESSION_ID,
    /** The JSON body contained a byte sequence that matches no token. */
    INVALID_TOKEN,
    /** The JSON body produced more tokens than the configured limit allows. */
    TOO_MANY_TOKENS,
    /** The body was non-empty, not JSON, and the configuration requires JSON. */
    UNSUPPORTED_CONTENT_TYPE
}
