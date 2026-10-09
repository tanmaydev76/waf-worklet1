package com.wafgateway.worklet1.automata;

import com.wafgateway.worklet1.regex.RegexNode;
import com.wafgateway.worklet1.regex.RegexParser;
import com.wafgateway.worklet1.regex.RegexSyntaxException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SubsetConstruction and Dfa: verifies the DFA agrees with the NFA
 * it was built from, and exercises each runtime method's documented contract.
 */
class SubsetConstructionTest {

    private static Nfa buildNfa(String pattern, int label) throws RegexSyntaxException {
        RegexNode node = RegexParser.parse(pattern);
        return ThompsonBuilder.build(node, label);
    }

    private static Dfa buildDfa(String pattern, MatchMode mode) throws RegexSyntaxException, StateLimitExceededException {
        return SubsetConstruction.build(buildNfa(pattern, 0), mode, 10_000);
    }

    // ========================================================================
    // DFA fullMatch agrees with NFA simulation
    // ========================================================================

    @Test
    void testFullMatchAgreesWithNfaSimulation_knownCases() throws Exception {
        String[][] cases = {
                {"abc", "abc", "true"}, {"abc", "ab", "false"}, {"abc", "abcd", "false"},
                {"a|b", "a", "true"}, {"a|b", "c", "false"},
                {"a*", "", "true"}, {"a*", "aaaa", "true"}, {"a*", "b", "false"},
                {"(ab)*c", "ababc", "true"}, {"(ab)*c", "abab", "false"},
                {"a+b?", "aab", "true"}, {"a+b?", "", "false"},
                {"[0-9]{2,3}", "12", "true"}, {"[0-9]{2,3}", "1234", "false"},
                {"\\d+(\\.\\d+)?", "123.45", "true"}, {"\\d+(\\.\\d+)?", ".5", "false"},
        };
        for (String[] c : cases) {
            Nfa nfa = buildNfa(c[0], 0);
            Dfa dfa = SubsetConstruction.build(nfa, MatchMode.FULL_MATCH, 10_000);
            byte[] bytes = c[1].getBytes(StandardCharsets.UTF_8);
            boolean expected = Boolean.parseBoolean(c[2]);
            assertEquals(!nfa.simulate(bytes).isEmpty(), dfa.fullMatch(bytes),
                    "NFA/DFA disagree for pattern=" + c[0] + " input=" + c[1]);
            assertEquals(expected, dfa.fullMatch(bytes),
                    "pattern=" + c[0] + " input=" + c[1]);
        }
    }

    @Test
    void testFullMatchAgreesWithNfaSimulation_randomized() throws Exception {
        // Fixed seed: deterministic across runs. Cross-checks many patterns
        // against many random inputs over a small alphabet that includes
        // regex metacharacters, so both matching and non-matching structured
        // inputs get exercised, not just noise.
        String[] patterns = {
                "abc", "a|b", "a*", "(ab)*c", "a+b?", "[0-9]{2,3}", "\\d+(\\.\\d+)?",
                "(abc|def)+", "[^0-9]+", "a{3}", "a{2,}", "x?y?z?", "(a|b|c)+[0-9]*",
                "[a-z][a-z0-9]*", ".*", "a.b", "(foo|bar|baz)", ""
        };
        Random rnd = new Random(42);
        String alphabet = "ab0.c+?*()[]{}xyzdef-";
        int trials = 0;

        for (String pattern : patterns) {
            Nfa nfa = buildNfa(pattern, 0);
            Dfa dfa = SubsetConstruction.build(nfa, MatchMode.FULL_MATCH, 10_000);
            for (int trial = 0; trial < 200; trial++) {
                int len = rnd.nextInt(8);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                }
                byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                boolean nfaResult = !nfa.simulate(bytes).isEmpty();
                boolean dfaResult = dfa.fullMatch(bytes);
                assertEquals(nfaResult, dfaResult,
                        "pattern=" + pattern + " input=\"" + sb + "\"");
                trials++;
            }
        }
        assertEquals(18 * 200, trials);
    }

    // ========================================================================
    // SEARCH mode
    // ========================================================================

    @Test
    void testSearchFindsHitAtRightIndex() throws Exception {
        Dfa dfa = buildDfa("<script", MatchMode.SEARCH);
        Dfa.SearchHit hit = dfa.search("abc<<script>".getBytes(StandardCharsets.UTF_8));
        assertNotNull(hit);
        // "<script" occurs starting at index 4 (the second '<'), 7 bytes long,
        // so the earliest accepting position is right after it, at index 11.
        assertEquals(11, hit.position());
    }

    @Test
    void testSearchNoHitOnIncompleteMatch() throws Exception {
        Dfa dfa = buildDfa("<script", MatchMode.SEARCH);
        Dfa.SearchHit hit = dfa.search("<scrip".getBytes(StandardCharsets.UTF_8));
        assertNull(hit);
    }

    @Test
    void testSearchNeverGetsStuck() throws Exception {
        // SEARCH-mode construction unions epsilon-closure(start) into every
        // transition, so garbage before/after/between should never prevent a
        // later match from being found.
        Dfa dfa = buildDfa("evil", MatchMode.SEARCH);
        assertNotNull(dfa.search("xxxxxxxxxxxxxxxxxxxxxxevilxxxxx".getBytes(StandardCharsets.UTF_8)));
        assertNull(dfa.search("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes(StandardCharsets.UTF_8)));
    }

    // ========================================================================
    // longestMatch
    // ========================================================================

    @Test
    void testLongestMatchDigitsFromStart() throws Exception {
        Dfa dfa = buildDfa("\\d+", MatchMode.FULL_MATCH);
        Dfa.LongestMatch lm = dfa.longestMatch("123abc".getBytes(StandardCharsets.UTF_8), 0);
        assertNotNull(lm);
        assertEquals(3, lm.endIndex());
    }

    @Test
    void testLongestMatchDigitsFromNonDigitPosition() throws Exception {
        Dfa dfa = buildDfa("\\d+", MatchMode.FULL_MATCH);
        Dfa.LongestMatch lm = dfa.longestMatch("123abc".getBytes(StandardCharsets.UTF_8), 3);
        assertNull(lm);
    }

    // ========================================================================
    // Labels: union of "if" (1) and "[a-z]+" (2)
    // ========================================================================

    @Test
    void testUnionLabels_bothMatchOnExactKeyword() throws Exception {
        Nfa ifNfa = buildNfa("if", 1);
        Nfa identNfa = buildNfa("[a-z]+", 2);
        Nfa combined = ThompsonBuilder.union(List.of(ifNfa, identNfa));
        Dfa dfa = SubsetConstruction.build(combined, MatchMode.FULL_MATCH, 10_000);

        Dfa.LongestMatch lm = dfa.longestMatch("if".getBytes(StandardCharsets.UTF_8), 0);
        assertNotNull(lm);
        assertEquals(2, lm.endIndex());
        assertArrayEquals(new int[]{1, 2}, lm.labels());
    }

    @Test
    void testUnionLabels_onlyIdentifierMatchesLongerWord() throws Exception {
        Nfa ifNfa = buildNfa("if", 1);
        Nfa identNfa = buildNfa("[a-z]+", 2);
        Nfa combined = ThompsonBuilder.union(List.of(ifNfa, identNfa));
        Dfa dfa = SubsetConstruction.build(combined, MatchMode.FULL_MATCH, 10_000);

        Dfa.LongestMatch lm = dfa.longestMatch("ifx".getBytes(StandardCharsets.UTF_8), 0);
        assertNotNull(lm);
        assertEquals(3, lm.endIndex());
        assertArrayEquals(new int[]{2}, lm.labels());
    }

    // ========================================================================
    // maxStates
    // ========================================================================

    @Test
    void testMaxStatesExceededThrows() throws Exception {
        Nfa nfa = buildNfa("a{0,50}b{0,50}c{0,50}", 0);
        assertThrows(StateLimitExceededException.class,
                () -> SubsetConstruction.build(nfa, MatchMode.FULL_MATCH, 5));
    }

    @Test
    void testMaxStatesNotExceededForSmallPattern() throws Exception {
        Nfa nfa = buildNfa("ab", 0);
        assertDoesNotThrow(() -> SubsetConstruction.build(nfa, MatchMode.FULL_MATCH, 10));
    }

    // ========================================================================
    // toDot
    // ========================================================================

    @Test
    void testToDotForSimplePattern() throws Exception {
        Dfa dfa = buildDfa("ab", MatchMode.FULL_MATCH);
        String dot = dfa.toDot();

        assertTrue(dot.startsWith("digraph DFA {"));
        assertTrue(dot.contains("->"));
        // Start state 0 has an edge labeled "a" leading somewhere, and that
        // target has an edge labeled "b" leading to a doublecircle (accepting) state.
        assertTrue(dot.contains("[label=\"a\"]"));
        assertTrue(dot.contains("[label=\"b\"]"));
        assertTrue(dot.contains("doublecircle"));
        // Dead state must never appear as a rendered node.
        assertFalse(dot.contains("shape=circle] [shape=circle]")); // sanity: no malformed duplicate
    }

    @Test
    void testToDotOmitsDeadState() throws Exception {
        Dfa dfa = buildDfa("ab", MatchMode.FULL_MATCH);
        // 4 reachable+dead states exist (start, dead, mid, end) but toDot only
        // declares shape nodes for the 3 non-dead ones.
        assertEquals(4, dfa.stateCount());
        String dot = dfa.toDot();
        long shapeDeclarations = dot.lines().filter(l -> l.contains("[shape=circle]") || l.contains("[shape=doublecircle]")).count();
        assertEquals(3, shapeDeclarations);
    }

    // ========================================================================
    // step() and stateCount() basics
    // ========================================================================

    @Test
    void testStepMatchesFullMatchBehavior() throws Exception {
        Dfa dfa = buildDfa("ab", MatchMode.FULL_MATCH);
        int s0 = dfa.start();
        int s1 = dfa.step(s0, 'a');
        int s2 = dfa.step(s1, 'b');
        assertTrue(dfa.fullMatch("ab".getBytes(StandardCharsets.UTF_8)));
        assertNotEquals(s0, s1);
        assertNotEquals(s1, s2);
        assertNotEquals(dfa.dead(), s1);
        assertNotEquals(dfa.dead(), s2);
    }

    @Test
    void testStateCountPositive() throws Exception {
        Dfa dfa = buildDfa("abc", MatchMode.FULL_MATCH);
        assertTrue(dfa.stateCount() > 0);
    }
}
