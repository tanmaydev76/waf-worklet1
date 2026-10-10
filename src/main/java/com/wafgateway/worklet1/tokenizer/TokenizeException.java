package com.wafgateway.worklet1.tokenizer;

/**
 * Thrown when a request body can't be tokenized: either a byte sequence
 * doesn't match any known token (not even partially), or the body produced
 * more tokens than the configured limit allows (a cheap defense against a
 * maliciously huge body being used to exhaust memory/time downstream).
 *
 * @author Worklet 1 Team
 */
public class TokenizeException extends Exception {

    /**
     * Why tokenizing failed.
     */
    public enum Reason {
        /** A byte sequence at some position doesn't match any token pattern. */
        INVALID_TOKEN,
        /** More tokens were produced than the caller's configured limit allows. */
        TOO_MANY_TOKENS
    }

    /** Which of the two failure kinds this is. */
    private final Reason reason;
    /** The byte offset in the original body where the failure was detected. */
    private final int position;

    /**
     * Creates the exception.
     *
     * @param message  human-readable explanation
     * @param reason   which of the two failure kinds this is
     * @param position the byte offset in the original body where the failure
     *                 was detected
     */
    public TokenizeException(String message, Reason reason, int position) {
        super(message);
        this.reason = reason;
        this.position = position;
    }

    /**
     * Returns why tokenizing failed.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * Returns the byte offset in the original body where the failure was detected.
     *
     * @return the byte position
     */
    public int position() {
        return position;
    }
}
