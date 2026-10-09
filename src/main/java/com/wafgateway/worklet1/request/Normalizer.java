package com.wafgateway.worklet1.request;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * Normalizes raw field bytes before signature scanning, so that attackers
 * cannot evade detection by disguising a known-bad pattern (e.g. a SQL
 * keyword or {@code <script>} tag) through encoding tricks.
 *
 * <p><b>Used only for scanning</b> — never for tokenizing, since the JSON
 * tokenizer (Phase 6) must see the exact original bytes to report correct
 * byte offsets. Normalization is lossy and reordering-aware by design.
 *
 * <p>Runs these steps, in this exact order, each a single linear pass over
 * the bytes (percent-decoding may repeat its pass up to 3 times):
 * <ol>
 *   <li>Percent-decode, repeated until a pass makes no further change (at
 *       most 3 passes) — this is what catches double-encoding tricks like
 *       {@code %252e%252e%252f} (encodes {@code %2e%2e%2f}, which itself
 *       decodes to {@code ../}).</li>
 *   <li>If this field came from a query string: {@code +} becomes a space
 *       (form-encoding convention; not applicable to other fields).</li>
 *   <li>Decode HTML entities: the named entities {@code &lt; &gt; &amp;
 *       &quot; &apos; &nbsp;} (each requires its trailing {@code ;}), and
 *       numeric entities {@code &#60;} / {@code &#x3c;} (trailing {@code ;}
 *       optional for these).</li>
 *   <li>Remove null bytes — a classic trick for smuggling payloads past
 *       naive string-based filters.</li>
 *   <li>Lowercase ASCII letters (so {@code UnIoN} and {@code union} are the
 *       same signature match).</li>
 *   <li>Replace SQL block comments ({@code /* ... *&#47;}) with a single
 *       space — catches {@code UnIoN/**&#47;SeLeCt} used to break up a
 *       keyword without using whitespace.</li>
 *   <li>Collapse any run of whitespace (space, tab, CR, LF, form feed,
 *       vertical tab) into a single space.</li>
 * </ol>
 *
 * @author Worklet 1 Team
 */
public final class Normalizer {

    private static final Map<String, Integer> NAMED_ENTITIES = Map.of(
            "lt", (int) '<',
            "gt", (int) '>',
            "amp", (int) '&',
            "quot", (int) '"',
            "apos", (int) '\'',
            "nbsp", 0xA0
    );

    private Normalizer() {
        // Utility class; not instantiable.
    }

    /**
     * Runs the full normalization pipeline described in the class Javadoc.
     *
     * @param input   the raw field bytes
     * @param isQuery whether this field came from a URL query string
     *                (controls the {@code +}-to-space step)
     * @return the normalized bytes
     */
    public static byte[] normalize(byte[] input, boolean isQuery) {
        byte[] result = percentDecode(input);
        if (isQuery) {
            result = plusToSpace(result);
        }
        result = decodeHtmlEntities(result);
        result = removeNullBytes(result);
        result = lowercaseAscii(result);
        result = replaceSqlComments(result);
        result = collapseWhitespace(result);
        return result;
    }

    // ========================================================================
    // Step 1: percent-decoding
    // ========================================================================

    private static byte[] percentDecode(byte[] input) {
        byte[] current = input;
        for (int round = 0; round < 3; round++) {
            byte[] next = percentDecodeOnce(current);
            if (Arrays.equals(next, current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    /**
     * Decodes {@code %XX} sequences in one left-to-right pass. An invalid
     * sequence (not followed by exactly 2 hex digits) is left untouched,
     * rather than throwing — a request shouldn't be rejected just because an
     * attacker (or a browser) sent a stray {@code %}.
     */
    private static byte[] percentDecodeOnce(byte[] input) {
        byte[] out = new byte[input.length];
        int outLen = 0;
        int i = 0;
        while (i < input.length) {
            byte b = input[i];
            if (b == '%' && i + 2 < input.length && isHexDigit(input[i + 1]) && isHexDigit(input[i + 2])) {
                out[outLen++] = (byte) ((hexValue(input[i + 1]) << 4) | hexValue(input[i + 2]));
                i += 3;
            } else {
                out[outLen++] = b;
                i += 1;
            }
        }
        return Arrays.copyOf(out, outLen);
    }

    // ========================================================================
    // Step 2: query '+' to space
    // ========================================================================

    private static byte[] plusToSpace(byte[] input) {
        byte[] out = input.clone();
        for (int i = 0; i < out.length; i++) {
            if (out[i] == '+') {
                out[i] = ' ';
            }
        }
        return out;
    }

    // ========================================================================
    // Step 3: HTML entity decoding
    // ========================================================================

    private static byte[] decodeHtmlEntities(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        int i = 0;
        while (i < input.length) {
            int consumed = (input[i] == '&') ? tryDecodeEntityAt(input, i, out) : 0;
            if (consumed > 0) {
                i += consumed;
            } else {
                out.write(input[i]);
                i += 1;
            }
        }
        return out.toByteArray();
    }

    /**
     * Tries to decode one HTML entity starting at {@code input[i]} (which is
     * {@code '&'}). Returns the number of input bytes the entity occupied
     * (so the caller can skip past it), or 0 if nothing valid was found here.
     */
    private static int tryDecodeEntityAt(byte[] input, int i, ByteArrayOutputStream out) {
        if (i + 1 < input.length && input[i + 1] == '#') {
            return tryDecodeNumericEntity(input, i, out);
        }
        return tryDecodeNamedEntity(input, i, out);
    }

    private static int tryDecodeNumericEntity(byte[] input, int i, ByteArrayOutputStream out) {
        int j = i + 2;
        boolean hex = false;
        if (j < input.length && (input[j] == 'x' || input[j] == 'X')) {
            hex = true;
            j++;
        }
        int digitsStart = j;

        // Leading zeros never affect the value, so skip them with no cap —
        // real HTML parsers do too (e.g. a browser decodes &#00000000060;
        // as '<', the same as &#60;). Only the *significant* digits need a
        // cap: 3 decimal digits (or 2 hex digits) is enough to express any
        // byte 0-255, so more significant digits than that means the value
        // is unambiguously out of range without needing to compute it exactly
        // (avoids overflow on a pathologically long digit run).
        while (j < input.length && input[j] == '0') {
            j++;
        }
        int significantStart = j;
        int maxSignificantDigits = hex ? 2 : 3;
        long value = 0;
        boolean outOfRange = false;
        while (j < input.length && isDigitFor(input[j], hex)) {
            if (j - significantStart < maxSignificantDigits) {
                value = value * (hex ? 16 : 10) + digitValue(input[j]);
            } else {
                outOfRange = true;
            }
            j++;
        }

        if (j == digitsStart) {
            return 0; // "&#" or "&#x" with no digits following: not an entity
        }
        int end = j;
        if (end < input.length && input[end] == ';') {
            end++;
        }
        if (!outOfRange && value >= 0 && value <= 255) {
            out.write((int) value);
            return end - i;
        }
        return 0; // out of byte range: leave the original text untouched
    }

    private static int tryDecodeNamedEntity(byte[] input, int i, ByteArrayOutputStream out) {
        for (Map.Entry<String, Integer> entry : NAMED_ENTITIES.entrySet()) {
            String name = entry.getKey();
            int nameStart = i + 1;
            int nameEnd = nameStart + name.length();
            if (nameEnd < input.length && input[nameEnd] == ';' && matchesAscii(input, nameStart, name)) {
                out.write(entry.getValue());
                return name.length() + 2; // '&' + name + ';'
            }
        }
        return 0;
    }

    private static boolean matchesAscii(byte[] input, int start, String ascii) {
        for (int k = 0; k < ascii.length(); k++) {
            if (input[start + k] != (byte) ascii.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigitFor(byte b, boolean hex) {
        if (b >= '0' && b <= '9') {
            return true;
        }
        return hex && ((b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F'));
    }

    private static int digitValue(byte b) {
        if (b >= '0' && b <= '9') {
            return b - '0';
        }
        if (b >= 'a' && b <= 'f') {
            return b - 'a' + 10;
        }
        return b - 'A' + 10;
    }

    private static boolean isHexDigit(byte b) {
        return isDigitFor(b, true);
    }

    private static int hexValue(byte b) {
        return digitValue(b);
    }

    // ========================================================================
    // Step 4: remove null bytes
    // ========================================================================

    private static byte[] removeNullBytes(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        for (byte b : input) {
            if (b != 0) {
                out.write(b);
            }
        }
        return out.toByteArray();
    }

    // ========================================================================
    // Step 5: lowercase ASCII letters
    // ========================================================================

    private static byte[] lowercaseAscii(byte[] input) {
        byte[] out = input.clone();
        for (int i = 0; i < out.length; i++) {
            byte b = out[i];
            if (b >= 'A' && b <= 'Z') {
                out[i] = (byte) (b + ('a' - 'A'));
            }
        }
        return out;
    }

    // ========================================================================
    // Step 6: replace SQL block comments with a single space
    // ========================================================================

    private static byte[] replaceSqlComments(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        int i = 0;
        while (i < input.length) {
            if (i + 1 < input.length && input[i] == '/' && input[i + 1] == '*') {
                int closeIdx = indexOfCommentClose(input, i + 2);
                out.write(' ');
                i = (closeIdx == -1) ? input.length : closeIdx + 2;
            } else {
                out.write(input[i]);
                i += 1;
            }
        }
        return out.toByteArray();
    }

    private static int indexOfCommentClose(byte[] input, int from) {
        for (int i = from; i + 1 < input.length; i++) {
            if (input[i] == '*' && input[i + 1] == '/') {
                return i;
            }
        }
        return -1;
    }

    // ========================================================================
    // Step 7: collapse whitespace runs
    // ========================================================================

    private static byte[] collapseWhitespace(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        boolean inWhitespace = false;
        for (byte b : input) {
            if (isWhitespace(b)) {
                if (!inWhitespace) {
                    out.write(' ');
                    inWhitespace = true;
                }
            } else {
                out.write(b);
                inWhitespace = false;
            }
        }
        return out.toByteArray();
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '\f' || b == 0x0B;
    }
}
