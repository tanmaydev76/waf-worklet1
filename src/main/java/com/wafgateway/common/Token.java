package com.wafgateway.common;

/**
 * A single token from a JSON body.
 *
 * <p>Represents one syntactic unit of JSON: a structural character (`{`, `}`, `[`, `]`, `:`, `,`),
 * a string literal, a number, or a keyword (`true`, `false`, `null`).
 *
 * <p>The {@code value} field holds the exact text from the original body (UTF-8 decoded),
 * e.g., `"\"bob\""` for a string token or `"25"` for a number. The {@code position}
 * is the byte offset in the original, un-normalized request body, allowing Worklet 2 to
 * map matches back to the raw input.
 *
 * @param type     the token type (structural, string, number, or keyword)
 * @param value    the exact text of the token from the body (UTF-8 decoded)
 * @param position byte offset in the original body where this token starts
 */
public record Token(TokenType type, String value, int position) {
    /**
     * Creates a token with validation.
     *
     * @param type     must not be null
     * @param value    must not be null
     * @param position must be non-negative
     */
    public Token {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (value == null) throw new IllegalArgumentException("value must not be null");
        if (position < 0) throw new IllegalArgumentException("position must be non-negative");
    }
}
