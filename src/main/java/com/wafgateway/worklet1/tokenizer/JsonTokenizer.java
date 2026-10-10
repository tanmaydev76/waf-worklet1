package com.wafgateway.worklet1.tokenizer;

import com.wafgateway.common.Token;
import com.wafgateway.common.TokenType;
import com.wafgateway.worklet1.automata.AutomataCompiler;
import com.wafgateway.worklet1.automata.Dfa;
import com.wafgateway.worklet1.automata.MatchMode;
import com.wafgateway.worklet1.automata.StateLimitExceededException;
import com.wafgateway.worklet1.regex.RegexSyntaxException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tokenizes a JSON request body using a single {@link MatchMode#FULL_MATCH}
 * DFA built from all token patterns at once.
 *
 * <p><b>Maximal munch:</b> at each position, {@link Dfa#longestMatch} finds
 * the longest prefix that matches *any* token pattern — this is what lets a
 * single DFA correctly tokenize without backtracking: e.g. at a {@code t},
 * the DFA is simultaneously tracking "could become {@code true}" and (if
 * {@code true} fails) nothing else, so there's no ambiguity to resolve.
 * When two different patterns match the exact same length at the same
 * position (only possible here if a future pattern is added that overlaps
 * an existing one), the pattern listed first in {@link #TOKEN_PATTERNS} wins
 * — enforced by picking the smallest label, since labels are assigned in
 * listing order.
 *
 * <p><b>No nesting tracking:</b> {@code {}} and {@code []} are emitted as
 * ordinary, independent tokens (LBRACE/RBRACE/LBRACKET/RBRACKET) with no
 * bookkeeping of how they pair up or how deep they're nested. Checking that
 * brackets are balanced and correctly structured is explicitly Worklet 2's
 * job (the context-free-grammar layer) — a regular-language tokenizer
 * fundamentally *cannot* verify arbitrary-depth nesting on its own (that
 * requires a stack, which is what makes CFGs strictly more powerful than
 * regular languages). So {@code {"a":[1,2}} tokenizes without complaint
 * here; only Worklet 2 would reject it.
 *
 * <p>Operates on the original, un-normalized body bytes (unlike signature
 * scanning) so that every token's {@code position} is a genuine offset into
 * what the client actually sent, and every token's {@code value} is the
 * exact original text.
 *
 * @author Worklet 1 Team
 */
public final class JsonTokenizer implements BodyTokenizer {

    /**
     * Token patterns in label order (0, 1, 2, ...) — labels 0-10 correspond
     * 1:1 with {@link TokenType#values()} (which is declared in this exact
     * same order), and label 11 is whitespace, recognized so it can be
     * skipped but never emitted as a {@link Token}.
     */
    private static final List<String> TOKEN_PATTERNS = List.of(
            "\\{",                                  // LBRACE
            "\\}",                                  // RBRACE
            "\\[",                                  // LBRACKET
            "\\]",                                  // RBRACKET
            ":",                                     // COLON
            ",",                                     // COMMA
            "\"([^\"\\\\]|\\\\.)*\"",                // STRING
            "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?",      // NUMBER
            "true",                                  // TRUE
            "false",                                 // FALSE
            "null",                                  // NULL
            "[ \\t\\r\\n]+"                           // WS (skipped)
    );

    private static final int WS_LABEL = 11;

    private final Dfa dfa;

    /**
     * Builds the tokenizer's DFA.
     *
     * @param maxStates the DFA state limit (see {@link AutomataCompiler})
     * @throws RegexSyntaxException        if a token pattern is malformed
     *                                      (should never happen for the
     *                                      fixed patterns above)
     * @throws StateLimitExceededException if the combined DFA needs too
     *                                      many states
     */
    public JsonTokenizer(int maxStates) throws RegexSyntaxException, StateLimitExceededException {
        List<Integer> labels = new ArrayList<>();
        for (int i = 0; i < TOKEN_PATTERNS.size(); i++) {
            labels.add(i);
        }
        AutomataCompiler compiler = new AutomataCompiler(maxStates);
        this.dfa = compiler.compileLabeled(TOKEN_PATTERNS, labels, MatchMode.FULL_MATCH);
    }

    /**
     * Tokenizes a body via maximal munch: repeatedly find the longest
     * matching token at the current position, emit it (unless it's
     * whitespace), and continue from right after it. O(n) — each byte is
     * examined by the DFA exactly once across the whole scan, since
     * {@code longestMatch} never backtracks past where it stopped.
     *
     * @param body      the raw, original body bytes
     * @param maxTokens the maximum number of tokens allowed
     * @return the tokens, in order
     * @throws TokenizeException if a position matches no token pattern at
     *                            all, or too many tokens were produced
     */
    @Override
    public List<Token> tokenize(byte[] body, int maxTokens) throws TokenizeException {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;

        while (pos < body.length) {
            Dfa.LongestMatch match = dfa.longestMatch(body, pos);
            if (match == null) {
                throw new TokenizeException(
                        "Invalid character at position " + pos,
                        TokenizeException.Reason.INVALID_TOKEN,
                        pos);
            }

            int label = Arrays.stream(match.labels()).min().orElseThrow();
            if (label != WS_LABEL) {
                TokenType type = TokenType.values()[label];
                String value = new String(body, pos, match.endIndex() - pos, StandardCharsets.UTF_8);
                tokens.add(new Token(type, value, pos));
                if (tokens.size() > maxTokens) {
                    throw new TokenizeException(
                            "Too many tokens (more than " + maxTokens + ")",
                            TokenizeException.Reason.TOO_MANY_TOKENS,
                            pos);
                }
            }

            pos = match.endIndex();
        }

        return tokens;
    }
}
