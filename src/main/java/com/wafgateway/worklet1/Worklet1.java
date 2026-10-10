package com.wafgateway.worklet1;

import com.wafgateway.common.HttpRequest;
import com.wafgateway.common.Token;
import com.wafgateway.worklet1.automata.StateLimitExceededException;
import com.wafgateway.worklet1.regex.RegexSyntaxException;
import com.wafgateway.worklet1.request.Field;
import com.wafgateway.worklet1.request.FieldExtractor;
import com.wafgateway.worklet1.session.SessionIdValidator;
import com.wafgateway.worklet1.signature.Match;
import com.wafgateway.worklet1.signature.Rule;
import com.wafgateway.worklet1.signature.RuleLoadException;
import com.wafgateway.worklet1.signature.RuleLoader;
import com.wafgateway.worklet1.signature.SignatureScanner;
import com.wafgateway.worklet1.tokenizer.JsonTokenizer;
import com.wafgateway.worklet1.tokenizer.TokenizeException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Worklet 1's single entry point: inspects one HTTP request and decides
 * whether to block it, using nothing but compiled DFAs under the hood (no
 * {@code java.util.regex}, no backtracking, no ReDoS exposure).
 *
 * <p>Construction does all the expensive, one-time work: loading
 * {@code rules.txt} and compiling every DFA this instance will ever use (one
 * per signature category, one for the JSON tokenizer, one for the session id
 * format). {@link #inspect} itself only ever runs already-compiled DFAs, so
 * it stays fast and allocation-light on the request path.
 *
 * @author Worklet 1 Team
 */
public final class Worklet1 {

    private final Worklet1Config config;
    private final SignatureScanner signatureScanner;
    private final JsonTokenizer jsonTokenizer;
    private final SessionIdValidator sessionIdValidator;

    /**
     * Builds a Worklet1 instance: loads rules and compiles every DFA once.
     *
     * @param config the configuration (limits, cookie name, policies, etc.)
     * @throws RuleLoadException           if {@code rules.txt} can't be loaded
     * @throws RegexSyntaxException        if a pattern is malformed
     * @throws StateLimitExceededException if a DFA needs too many states
     */
    public Worklet1(Worklet1Config config)
            throws RuleLoadException, RegexSyntaxException, StateLimitExceededException {
        this.config = config;
        List<Rule> rules = RuleLoader.load(config.rulesFile());
        this.signatureScanner = new SignatureScanner(rules, config.dfaMaxStates());
        this.jsonTokenizer = new JsonTokenizer(config.dfaMaxStates());
        this.sessionIdValidator = new SessionIdValidator(config.dfaMaxStates());
    }

    /**
     * Inspects one request. Never throws — any unexpected failure becomes a
     * block with a clear explanation, rather than propagating an exception
     * into the caller's request-handling path.
     *
     * <p>Order (cheapest checks first, so an obviously-bad request is
     * rejected before any expensive work):
     * <ol>
     *   <li>Body size against {@code limits.maxBodyBytes}</li>
     *   <li>Extract fields</li>
     *   <li>Signature scan; block if any match reaches {@code block.minSeverity}</li>
     *   <li>Session cookie format, if present</li>
     *   <li>JSON tokenizing, if the body is non-empty and looks like JSON
     *       (otherwise governed by {@code body.nonJson})</li>
     * </ol>
     *
     * @param request the request to inspect
     * @return the result; never null
     */
    public Worklet1Result inspect(HttpRequest request) {
        try {
            return doInspect(request);
        } catch (Exception e) {
            return blocked(null, "Unexpected error during inspection: " + e);
        }
    }

    private Worklet1Result doInspect(HttpRequest request) {
        // [1] Body size.
        byte[] body = request.body();
        if (body.length > config.maxBodyBytes()) {
            return blocked(BlockReason.BODY_TOO_LARGE,
                    "Body size " + body.length + " exceeds limit " + config.maxBodyBytes());
        }

        // [2] Extract fields.
        List<Field> fields = FieldExtractor.extract(request);

        // [3]+[4] Signature scan; block if any match reaches the severity threshold.
        // All matches found are reported, not just the ones that triggered blocking.
        List<Match> matches = signatureScanner.scan(fields);
        boolean severeEnough = matches.stream()
                .anyMatch(m -> m.severity().ordinal() >= config.blockMinSeverity().ordinal());
        if (severeEnough) {
            return new Worklet1Result(true, BlockReason.SIGNATURE, matches,
                    "Signature match: " + matches.get(0).ruleId() + " in field " + matches.get(0).fieldName(),
                    List.of(), null);
        }

        // [5] Session cookie format, if present.
        String cookieValue = request.cookies().get(config.sessionCookieName());
        String sessionId = null;
        if (cookieValue != null) {
            if (!sessionIdValidator.isValid(cookieValue)) {
                return blocked(BlockReason.BAD_SESSION_ID,
                        "Cookie '" + config.sessionCookieName() + "' is not a valid session id");
            }
            sessionId = cookieValue;
        }

        // [6] JSON tokenizing, only if there's a body at all.
        List<Token> tokens = List.of();
        if (body.length > 0) {
            String contentType = findHeaderIgnoreCase(request.headers(), "content-type");
            if (isJsonContentType(contentType)) {
                try {
                    tokens = jsonTokenizer.tokenize(body, config.maxTokens());
                } catch (TokenizeException e) {
                    BlockReason reason = (e.reason() == TokenizeException.Reason.INVALID_TOKEN)
                            ? BlockReason.INVALID_TOKEN
                            : BlockReason.TOO_MANY_TOKENS;
                    return blocked(reason, "Tokenizing failed (" + e.reason() + ") at position " + e.position());
                }
            } else if (config.bodyNonJson() == Worklet1Config.NonJsonPolicy.BLOCK) {
                return blocked(BlockReason.UNSUPPORTED_CONTENT_TYPE,
                        "Content-Type '" + contentType + "' is not application/json");
            }
            // else: ALLOW policy, tokens stays empty.
        }

        // [7] Pass.
        return new Worklet1Result(false, null, List.of(), null, tokens, sessionId);
    }

    /**
     * Checks whether a Content-Type value is {@code application/json},
     * ignoring case and any trailing parameters (e.g. {@code ; charset=utf-8}).
     */
    private static boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        String prefix = "application/json";
        if (!normalized.startsWith(prefix)) {
            return false;
        }
        // Must be an exact match, or immediately followed by a parameter
        // separator - not just any longer content-type that happens to
        // start with the same text (e.g. "application/json-patch+json").
        if (normalized.length() == prefix.length()) {
            return true;
        }
        char next = normalized.charAt(prefix.length());
        return next == ';' || Character.isWhitespace(next);
    }

    private static String findHeaderIgnoreCase(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Worklet1Result blocked(BlockReason reason, String detail) {
        return new Worklet1Result(true, reason, List.of(), detail, List.of(), null);
    }
}
