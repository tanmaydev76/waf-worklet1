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
 * Tests for HopcroftMinimizer: verifies the minimized DFA recognizes the
 * same language as the original, never has more states, and never merges
 * states that carry different labels.
 */
class HopcroftMinimizerTest {

    private static Dfa buildDfa(String pattern, MatchMode mode) throws RegexSyntaxException, StateLimitExceededException {
        RegexNode node = RegexParser.parse(pattern);
        Nfa nfa = ThompsonBuilder.build(node, 0);
        return SubsetConstruction.build(nfa, mode, 10_000);
    }

    private static final String[] TEST_PATTERNS = {
            "abc", "a|b", "a*", "(ab)*c", "a+b?", "[0-9]{2,3}", "\\d+(\\.\\d+)?",
            "(abc|def)+", "[^0-9]+", "a{3}", "a{2,}", "x?y?z?", "(a|b|c)+[0-9]*",
            "[a-z][a-z0-9]*", ".*", "a.b", "(foo|bar|baz)", "", "(a|b)*abb"
    };

    // ========================================================================
    // Minimized DFA agrees with the original on every input
    // ========================================================================

    @Test
    void testMinimizedAgreesWithOriginal_randomized() throws Exception {
        Random rnd = new Random(42);
        String alphabet = "ab0.c+?*()[]{}xyzdef-";
        int trials = 0;

        for (String pattern : TEST_PATTERNS) {
            Dfa dfa = buildDfa(pattern, MatchMode.FULL_MATCH);
            Dfa min = HopcroftMinimizer.minimize(dfa);
            for (int trial = 0; trial < 200; trial++) {
                int len = rnd.nextInt(8);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                }
                byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                assertEquals(dfa.fullMatch(bytes), min.fullMatch(bytes),
                        "pattern=" + pattern + " input=\"" + sb + "\"");
                trials++;
            }
        }
        assertEquals(TEST_PATTERNS.length * 200, trials);
    }

    @Test
    void testMinimizedAgreesWithOriginal_search() throws Exception {
        Dfa dfa = buildDfa("<script", MatchMode.SEARCH);
        Dfa min = HopcroftMinimizer.minimize(dfa);

        String[] inputs = {"abc<<script>", "<scrip", "nothing here", "<script>alert(1)"};
        for (String input : inputs) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            Dfa.SearchHit origHit = dfa.search(bytes);
            Dfa.SearchHit minHit = min.search(bytes);
            if (origHit == null) {
                assertNull(minHit, "input=" + input);
            } else {
                assertNotNull(minHit, "input=" + input);
                assertEquals(origHit.position(), minHit.position(), "input=" + input);
            }
        }
    }

    // ========================================================================
    // Minimized state count never exceeds the original
    // ========================================================================

    @Test
    void testMinimizedStateCountNeverExceedsOriginal() throws Exception {
        for (String pattern : TEST_PATTERNS) {
            Dfa dfa = buildDfa(pattern, MatchMode.FULL_MATCH);
            Dfa min = HopcroftMinimizer.minimize(dfa);
            assertTrue(min.stateCount() <= dfa.stateCount(),
                    "pattern=" + pattern + " dfa=" + dfa.stateCount() + " min=" + min.stateCount());
        }
    }

    // ========================================================================
    // Textbook example: (a|b)*abb
    // ========================================================================

    @Test
    void testTextbookExample_aOrBStarAbb() throws Exception {
        Dfa dfa = buildDfa("(a|b)*abb", MatchMode.FULL_MATCH);
        Dfa min = HopcroftMinimizer.minimize(dfa);
        // The classic compilers-textbook minimal DFA for (a|b)*abb has 4 live
        // states plus 1 dead state for any byte outside {a,b} (our alphabet
        // is all 256 bytes, unlike the textbook's {a,b}-only alphabet).
        assertEquals(5, min.stateCount());

        assertTrue(min.fullMatch("abb".getBytes(StandardCharsets.UTF_8)));
        assertTrue(min.fullMatch("aababb".getBytes(StandardCharsets.UTF_8)));
        assertFalse(min.fullMatch("ab".getBytes(StandardCharsets.UTF_8)));
        assertFalse(min.fullMatch("abbb".getBytes(StandardCharsets.UTF_8)));
    }

    // ========================================================================
    // Equivalent patterns minimize to the same size
    // ========================================================================

    @Test
    void testEquivalentPatternsMinimizeToSameSize() throws Exception {
        Dfa star = HopcroftMinimizer.minimize(buildDfa("a*", MatchMode.FULL_MATCH));
        Dfa doubleStar = HopcroftMinimizer.minimize(buildDfa("(a*)*", MatchMode.FULL_MATCH));
        Dfa orSelf = HopcroftMinimizer.minimize(buildDfa("(a|a)*", MatchMode.FULL_MATCH));

        assertEquals(star.stateCount(), doubleStar.stateCount());
        assertEquals(star.stateCount(), orSelf.stateCount());
        // 1 live accepting state (loops on 'a') + 1 dead state for anything else.
        assertEquals(2, star.stateCount());
    }

    // ========================================================================
    // States with different labels are never merged
    // ========================================================================

    @Test
    void testLabelsNeverMerged_bothActiveOnSharedPrefix() throws Exception {
        Nfa ifNfa = ThompsonBuilder.build(RegexParser.parse("if"), 1);
        Nfa identNfa = ThompsonBuilder.build(RegexParser.parse("[a-z]+"), 2);
        Nfa combined = ThompsonBuilder.union(List.of(ifNfa, identNfa));
        Dfa dfa = SubsetConstruction.build(combined, MatchMode.FULL_MATCH, 10_000);
        Dfa min = HopcroftMinimizer.minimize(dfa);

        Dfa.LongestMatch lmIf = min.longestMatch("if".getBytes(StandardCharsets.UTF_8), 0);
        assertArrayEquals(new int[]{1, 2}, lmIf.labels());

        Dfa.LongestMatch lmIfx = min.longestMatch("ifx".getBytes(StandardCharsets.UTF_8), 0);
        assertArrayEquals(new int[]{2}, lmIfx.labels());
    }

    @Test
    void testLabelsNeverMerged_distinctRulesStayDistinguishable() throws Exception {
        Nfa catNfa = ThompsonBuilder.build(RegexParser.parse("cat"), 1);
        Nfa carNfa = ThompsonBuilder.build(RegexParser.parse("car"), 2);
        Nfa combined = ThompsonBuilder.union(List.of(catNfa, carNfa));
        Dfa dfa = SubsetConstruction.build(combined, MatchMode.FULL_MATCH, 10_000);
        Dfa min = HopcroftMinimizer.minimize(dfa);

        assertArrayEquals(new int[]{1}, min.longestMatch("cat".getBytes(StandardCharsets.UTF_8), 0).labels());
        assertArrayEquals(new int[]{2}, min.longestMatch("car".getBytes(StandardCharsets.UTF_8), 0).labels());
    }

    // ========================================================================
    // Dead state and start renumbering invariants
    // ========================================================================

    @Test
    void testMinimizedDfaStillRejectsViaDeadState() throws Exception {
        Dfa dfa = buildDfa("abc", MatchMode.FULL_MATCH);
        Dfa min = HopcroftMinimizer.minimize(dfa);
        assertFalse(min.fullMatch("xyz".getBytes(StandardCharsets.UTF_8)));
        assertFalse(min.fullMatch("abcd".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testMinimizedDfaWorksForSearchModeWithUnreachableDeadState() throws Exception {
        // SEARCH-mode DFAs have an unreachable dead state (see SubsetConstruction);
        // minimization must still produce a valid Dfa object with a usable
        // dead state, not crash or lose it.
        Dfa dfa = buildDfa("xyz", MatchMode.SEARCH);
        Dfa min = HopcroftMinimizer.minimize(dfa);
        assertNotNull(min.search("abcxyzdef".getBytes(StandardCharsets.UTF_8)));
        assertNull(min.search("abcdef".getBytes(StandardCharsets.UTF_8)));
    }
}
