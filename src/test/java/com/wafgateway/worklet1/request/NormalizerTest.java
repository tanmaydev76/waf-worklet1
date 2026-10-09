package com.wafgateway.worklet1.request;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Normalizer: each pipeline step in isolation, plus the combined
 * disguised-attack cases the whole pipeline exists to catch.
 */
class NormalizerTest {

    private static String normalize(String input, boolean isQuery) {
        byte[] result = Normalizer.normalize(input.getBytes(StandardCharsets.UTF_8), isQuery);
        return new String(result, StandardCharsets.UTF_8);
    }

    // ========================================================================
    // Step 1: percent-decoding
    // ========================================================================

    @Test
    void testPercentDecodeSingleRound() {
        assertEquals("<", normalize("%3c", false));
    }

    @Test
    void testPercentDecodeExactCaseFromSpec() {
        assertEquals("' or 1=1", normalize("%27%20OR%201%3D1", false));
    }

    @Test
    void testPercentDecodeDoubleEncoding() {
        // %25 decodes to '%', requiring a 2nd round to decode %2e%2e%2f -> ../
        assertEquals("../", normalize("%252e%252e%252f", false));
    }

    @Test
    void testPercentDecodeInvalidSequenceLeftUnchanged() {
        assertEquals("100%", normalize("100%", false));
        assertEquals("%zz", normalize("%zz", false));
    }

    @Test
    void testPercentDecodeTrailingPercentNoCrash() {
        assertEquals("abc%", normalize("abc%", false));
        assertEquals("abc%4", normalize("abc%4", false));
    }

    // ========================================================================
    // Step 2: query '+' to space
    // ========================================================================

    @Test
    void testPlusToSpaceOnlyWhenQuery() {
        assertEquals("a b", normalize("a+b", true));
        assertEquals("a+b", normalize("a+b", false));
    }

    // ========================================================================
    // Step 3: HTML entity decoding
    // ========================================================================

    @Test
    void testNamedEntities() {
        assertEquals("<script>", normalize("&lt;script&gt;", false));
        assertEquals("&", normalize("&amp;", false));
        assertEquals("\"", normalize("&quot;", false));
        assertEquals("'", normalize("&apos;", false));
    }

    @Test
    void testNumericEntitiesDecimalAndHex() {
        assertEquals("<script>", normalize("&#60;script&#x3e;", false));
    }

    @Test
    void testNumericEntityWithoutTrailingSemicolon() {
        byte[] result = Normalizer.normalize("&#60script".getBytes(StandardCharsets.UTF_8), false);
        assertEquals("<script", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void testEntityOutOfByteRangeLeftUnchanged() {
        String result = normalize("&#99999;", false);
        assertEquals("&#99999;", result);
    }

    @Test
    void testNamedEntityWithoutSemicolonNotDecoded() {
        // Named entities require the trailing ';' per this engine's design.
        assertEquals("&ltscript", normalize("&ltscript", false));
    }

    @Test
    void testNumericEntityIgnoresArbitraryLeadingZeros() {
        // A real HTML parser (browser) doesn't cap digit consumption by
        // count - leading zeros never change the value. &#60; padded with
        // 9 leading zeros must still decode to '<', the same as &#60;.
        // (Regression: an earlier version capped at 10 total digits,
        // truncating this to the wrong value instead of decoding it correctly.)
        assertEquals("<", normalize("&#00000000060;", false));
        assertEquals("<script>", normalize("&#000000000000000060;script&#x0000003e;", false));
    }

    @Test
    void testNumericEntityOutOfRangeWithLeadingZerosStillRejected() {
        // 600 is out of byte range regardless of how it's padded.
        assertEquals("&#0000000600;", normalize("&#0000000600;", false));
    }

    // ========================================================================
    // Step 4: null byte removal
    // ========================================================================

    @Test
    void testNullByteRemoval() {
        byte[] input = "<scr\0ipt>".getBytes(StandardCharsets.UTF_8);
        byte[] result = Normalizer.normalize(input, false);
        assertEquals("<script>", new String(result, StandardCharsets.UTF_8));
    }

    // ========================================================================
    // Step 5: lowercase ASCII
    // ========================================================================

    @Test
    void testLowercaseAscii() {
        assertEquals("union select", normalize("UNION SELECT", false));
    }

    // ========================================================================
    // Step 6: SQL block comments
    // ========================================================================

    @Test
    void testSqlCommentReplacedWithSpace() {
        assertEquals("union select", normalize("UnIoN/**/SeLeCt", false));
    }

    @Test
    void testSqlCommentWithContentInside() {
        assertEquals("a b", normalize("a/* comment */b", false));
    }

    @Test
    void testUnclosedSqlCommentConsumesRest() {
        assertEquals("a", normalize("a/*never closed", false).trim());
    }

    // ========================================================================
    // Step 7: whitespace collapsing
    // ========================================================================

    @Test
    void testWhitespaceCollapsing() {
        assertEquals("a b", normalize("a   \t\n  b", false));
    }

    // ========================================================================
    // Combined disguised-attack cases from the spec
    // ========================================================================

    @Test
    void testCombinedCase_percentEncodedSqlInjection() {
        assertEquals("' or 1=1", normalize("%27%20OR%201%3D1", false));
    }

    @Test
    void testCombinedCase_doublePercentEncodedPathTraversal() {
        assertEquals("../", normalize("%252e%252e%252f", false));
    }

    @Test
    void testCombinedCase_sqlCommentObfuscatedKeywords() {
        assertEquals("union select", normalize("UnIoN/**/SeLeCt", false));
    }

    @Test
    void testCombinedCase_htmlEntityScriptTag() {
        assertEquals("<script>", normalize("&lt;script&gt;", false));
        assertEquals("<script>", normalize("&#60;script&#x3e;", false));
    }

    @Test
    void testCombinedCase_nullByteInjection() {
        assertEquals("<script>", normalize("<scr\0ipt>", false));
    }

    @Test
    void testCombinedCase_plusOnlyForQuery() {
        assertEquals("a b", normalize("a+b", true));
        assertEquals("a+b", normalize("a+b", false));
    }

    @Test
    void testCombinedCase_mixedWhitespaceRun() {
        assertEquals("a b", normalize("a   \t\n  b", false));
    }

    @Test
    void testCombinedCase_invalidPercentEscapesUnchanged() {
        assertEquals("100%", normalize("100%", false));
        assertEquals("%zz", normalize("%zz", false));
    }

    // ========================================================================
    // Step ordering sanity: percent-decode happens before entity decode,
    // before null-removal, before lowercasing, before comment stripping,
    // before whitespace collapsing.
    // ========================================================================

    @Test
    void testStepOrdering_percentDecodedTextCanFormHtmlEntity() {
        // %26lt%3b decodes (step 1) to "&lt;", which step 3 then decodes to "<"
        assertEquals("<", normalize("%26lt%3b", false));
    }

    @Test
    void testStepOrdering_percentDecodedTextCanFormSqlComment() {
        // %2f* decodes (step 1) to "/*", starting a comment that step 6 handles
        assertEquals("a b", normalize("a%2f* x */b", false));
    }

    @Test
    void testStepOrdering_lowercaseDoesNotAffectEntityNames() {
        // Entity names are matched before lowercasing in the pipeline (step 3
        // before step 5), so decoding isn't affected by case; this just checks
        // uppercase surrounding text still lowercases correctly after entities resolve.
        assertEquals("<b>bold</b>", normalize("&lt;B&gt;BOLD&lt;/B&gt;", false));
    }
}
