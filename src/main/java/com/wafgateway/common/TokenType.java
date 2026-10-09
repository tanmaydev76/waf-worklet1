package com.wafgateway.common;

/**
 * JSON token types recognized by the Worklet 1 tokenizer.
 *
 * These are the basic structural and value tokens produced by parsing a JSON body.
 * Worklet 2 (context-free grammar validator) receives these tokens to check structure
 * and detect JSON bombs. Only used when body Content-Type is application/json.
 */
public enum TokenType {
    /** Left brace `{` */
    LBRACE,
    /** Right brace `}` */
    RBRACE,
    /** Left bracket `[` */
    LBRACKET,
    /** Right bracket `]` */
    RBRACKET,
    /** Colon `:` (object key-value separator) */
    COLON,
    /** Comma `,` (value separator) */
    COMMA,
    /** String literal `"..."` */
    STRING,
    /** Number literal (integer, decimal, or exponent notation) */
    NUMBER,
    /** Boolean `true` */
    TRUE,
    /** Boolean `false` */
    FALSE,
    /** Null value `null` */
    NULL
}
