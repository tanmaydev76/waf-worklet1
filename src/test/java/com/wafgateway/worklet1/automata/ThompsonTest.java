package com.wafgateway.worklet1.automata;

import com.wafgateway.worklet1.regex.RegexNode;
import com.wafgateway.worklet1.regex.RegexParser;
import com.wafgateway.worklet1.regex.RegexSyntaxException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ThompsonBuilder: verifies that NFAs built from regex trees accept
 * and reject the correct strings, using Nfa.simulate() (full match semantics).
 */
class ThompsonTest {

    private static Nfa compile(String pattern) throws RegexSyntaxException {
        RegexNode node = RegexParser.parse(pattern);
        return ThompsonBuilder.build(node, 0);
    }

    private static boolean accepts(Nfa nfa, String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return !nfa.simulate(bytes).isEmpty();
    }

    // ========================================================================
    // "abc" — plain concatenation
    // ========================================================================

    @Test
    void testLiteralConcatenation() throws RegexSyntaxException {
        Nfa nfa = compile("abc");
        assertTrue(accepts(nfa, "abc"));
        assertFalse(accepts(nfa, "ab"));
        assertFalse(accepts(nfa, "abcd"));
        assertFalse(accepts(nfa, "xyz"));
        assertFalse(accepts(nfa, ""));
    }

    // ========================================================================
    // "a|b" — union
    // ========================================================================

    @Test
    void testUnion() throws RegexSyntaxException {
        Nfa nfa = compile("a|b");
        assertTrue(accepts(nfa, "a"));
        assertTrue(accepts(nfa, "b"));
        assertFalse(accepts(nfa, "ab"));
        assertFalse(accepts(nfa, "c"));
        assertFalse(accepts(nfa, ""));
    }

    // ========================================================================
    // "a*" — star
    // ========================================================================

    @Test
    void testStar() throws RegexSyntaxException {
        Nfa nfa = compile("a*");
        assertTrue(accepts(nfa, ""));
        assertTrue(accepts(nfa, "a"));
        assertTrue(accepts(nfa, "aaaa"));
        assertFalse(accepts(nfa, "b"));
        assertFalse(accepts(nfa, "ab"));
    }

    // ========================================================================
    // "(ab)*c" — group + star + concat
    // ========================================================================

    @Test
    void testGroupedStarConcat() throws RegexSyntaxException {
        Nfa nfa = compile("(ab)*c");
        assertTrue(accepts(nfa, "c"));
        assertTrue(accepts(nfa, "abc"));
        assertTrue(accepts(nfa, "ababc"));
        assertFalse(accepts(nfa, "ab"));
        assertFalse(accepts(nfa, "abcc"));
        assertFalse(accepts(nfa, "abab"));
    }

    // ========================================================================
    // "a+b?" — plus and optional (desugared)
    // ========================================================================

    @Test
    void testPlusAndOptional() throws RegexSyntaxException {
        Nfa nfa = compile("a+b?");
        assertTrue(accepts(nfa, "a"));
        assertTrue(accepts(nfa, "aa"));
        assertTrue(accepts(nfa, "ab"));
        assertTrue(accepts(nfa, "aab"));
        assertFalse(accepts(nfa, ""));
        assertFalse(accepts(nfa, "b"));
        assertFalse(accepts(nfa, "aba"));
    }

    // ========================================================================
    // "[0-9]{2,3}" — character class + bounded counter
    // ========================================================================

    @Test
    void testDigitCounter() throws RegexSyntaxException {
        Nfa nfa = compile("[0-9]{2,3}");
        assertTrue(accepts(nfa, "12"));
        assertTrue(accepts(nfa, "123"));
        assertFalse(accepts(nfa, "1"));
        assertFalse(accepts(nfa, "1234"));
        assertFalse(accepts(nfa, "ab"));
    }

    // ========================================================================
    // "\d+(\.\d+)?" — a float-like pattern (unbounded plus + optional group)
    // ========================================================================

    @Test
    void testFloatPattern() throws RegexSyntaxException {
        Nfa nfa = compile("\\d+(\\.\\d+)?");
        assertTrue(accepts(nfa, "123"));
        assertTrue(accepts(nfa, "123.45"));
        assertTrue(accepts(nfa, "1.2"));
        assertFalse(accepts(nfa, ""));
        assertFalse(accepts(nfa, ".5"));
        assertFalse(accepts(nfa, "123."));
    }

    // ========================================================================
    // Union of multiple labeled patterns
    // ========================================================================

    @Test
    void testUnionOfLabeledPatterns() throws RegexSyntaxException {
        Nfa catNfa = ThompsonBuilder.build(RegexParser.parse("cat"), 1);
        Nfa carNfa = ThompsonBuilder.build(RegexParser.parse("car"), 2);
        Nfa combined = ThompsonBuilder.union(List.of(catNfa, carNfa));

        SortedSet<Integer> carLabels = combined.simulate("car".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, carLabels.size());
        assertTrue(carLabels.contains(2));
        assertFalse(carLabels.contains(1));

        SortedSet<Integer> catLabels = combined.simulate("cat".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, catLabels.size());
        assertTrue(catLabels.contains(1));
        assertFalse(catLabels.contains(2));

        // Neither pattern matches "cow"
        assertTrue(combined.simulate("cow".getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    @Test
    void testUnionOfThreePatterns() throws RegexSyntaxException {
        Nfa a = ThompsonBuilder.build(RegexParser.parse("foo"), 10);
        Nfa b = ThompsonBuilder.build(RegexParser.parse("bar"), 20);
        Nfa c = ThompsonBuilder.build(RegexParser.parse("baz"), 30);
        Nfa combined = ThompsonBuilder.union(List.of(a, b, c));

        assertEquals(List.of(10), combined.simulate("foo".getBytes(StandardCharsets.UTF_8)).stream().toList());
        assertEquals(List.of(20), combined.simulate("bar".getBytes(StandardCharsets.UTF_8)).stream().toList());
        assertEquals(List.of(30), combined.simulate("baz".getBytes(StandardCharsets.UTF_8)).stream().toList());
    }

    // ========================================================================
    // State count grows linearly with pattern length
    // ========================================================================

    @Test
    void testStateCountGrowsLinearlyForLiteral() throws RegexSyntaxException {
        // Each literal character becomes a Symbol fragment of exactly 2 states
        // (start, end); concatenation links them via epsilon without adding
        // new states. So an n-character literal should have exactly 2n states.
        String longLiteral = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"; // 63 chars
        Nfa nfa = compile(longLiteral);
        assertEquals(2 * longLiteral.length(), nfa.stateCount());
    }

    @Test
    void testStateCountScalesRoughlyLinearly() throws RegexSyntaxException {
        String short10 = "a".repeat(10);
        String long100 = "a".repeat(100);
        Nfa shortNfa = compile(short10);
        Nfa longNfa = compile(long100);

        // 10x more characters should mean ~10x more states, not exponentially more.
        double ratio = (double) longNfa.stateCount() / shortNfa.stateCount();
        assertEquals(10.0, ratio, 0.01);
    }

    // ========================================================================
    // Simulation sanity: epsilon-only pattern, empty pattern
    // ========================================================================

    @Test
    void testEmptyPatternAcceptsOnlyEmptyString() throws RegexSyntaxException {
        Nfa nfa = compile("");
        assertTrue(accepts(nfa, ""));
        assertFalse(accepts(nfa, "a"));
    }

    @Test
    void testOptionalDesugarAcceptsBoth() throws RegexSyntaxException {
        Nfa nfa = compile("a?");
        assertTrue(accepts(nfa, ""));
        assertTrue(accepts(nfa, "a"));
        assertFalse(accepts(nfa, "aa"));
    }
}
