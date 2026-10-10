package com.wafgateway.worklet1.tokenizer;

import com.wafgateway.common.Token;

import java.util.List;

/**
 * Splits a request body into a flat list of tokens, for Worklet 2 (the
 * context-free-grammar layer) to check structure.
 *
 * @author Worklet 1 Team
 */
public interface BodyTokenizer {

    /**
     * Tokenizes a body.
     *
     * @param body      the raw, original (not normalized) body bytes
     * @param maxTokens the maximum number of tokens allowed before giving up
     * @return the tokens, in order
     * @throws TokenizeException if a byte sequence doesn't match any token,
     *                            or too many tokens were produced
     */
    List<Token> tokenize(byte[] body, int maxTokens) throws TokenizeException;
}
