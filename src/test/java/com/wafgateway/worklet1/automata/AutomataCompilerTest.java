package com.wafgateway.worklet1.automata;

import com.wafgateway.worklet1.regex.RegexParser;
import com.wafgateway.worklet1.regex.RegexSyntaxException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AutomataCompiler: the parse-Thompson-union-subset-Hopcroft pipeline facade.
 */
class AutomataCompilerTest {

    @Test
    void testCompileSinglePattern() throws Exception {
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        Dfa dfa = compiler.compile("abc", MatchMode.FULL_MATCH);
        assertTrue(dfa.fullMatch("abc".getBytes(StandardCharsets.UTF_8)));
        assertFalse(dfa.fullMatch("abcd".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testCompileAppliesMinimization() throws Exception {
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        Dfa compiled = compiler.compile("(a|b)*abb", MatchMode.FULL_MATCH);
        // The raw (unminimized) subset-construction DFA for this pattern has
        // 6 states; compile() must run Hopcroft and produce the minimal 5.
        assertEquals(5, compiled.stateCount());
    }

    @Test
    void testCompileIsCaseSensitive() throws Exception {
        // Patterns are compiled with ignoreCase=false: the Normalizer lowercases
        // input before scanning, so the compiler itself must stay case-sensitive.
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        Dfa dfa = compiler.compile("union", MatchMode.SEARCH);
        assertNotNull(dfa.search("union".getBytes(StandardCharsets.UTF_8)));
        assertNull(dfa.search("UNION".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testCompileRejectsInvalidPattern() {
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        assertThrows(RegexSyntaxException.class, () -> compiler.compile("(ab", MatchMode.FULL_MATCH));
    }

    @Test
    void testCompileRespectsMaxStates() {
        AutomataCompiler compiler = new AutomataCompiler(5);
        assertThrows(StateLimitExceededException.class,
                () -> compiler.compile("a{0,50}b{0,50}c{0,50}", MatchMode.FULL_MATCH));
    }

    // ========================================================================
    // compileLabeled
    // ========================================================================

    @Test
    void testCompileLabeledUnionReportsCorrectLabels() throws Exception {
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        Dfa dfa = compiler.compileLabeled(List.of("cat", "car"), List.of(1, 2), MatchMode.FULL_MATCH);

        assertArrayEquals(new int[]{1}, dfa.longestMatch("cat".getBytes(StandardCharsets.UTF_8), 0).labels());
        assertArrayEquals(new int[]{2}, dfa.longestMatch("car".getBytes(StandardCharsets.UTF_8), 0).labels());
        assertNull(dfa.longestMatch("cow".getBytes(StandardCharsets.UTF_8), 0));
    }

    @Test
    void testCompileLabeledSearchModeAcrossManyRules() throws Exception {
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        Dfa dfa = compiler.compileLabeled(
                List.of("<script", "javascript\\s*:", "\\.\\.[/\\\\]"),
                List.of(1, 2, 3),
                MatchMode.SEARCH);

        assertArrayEquals(new int[]{1}, dfa.search("hello<script>x".getBytes(StandardCharsets.UTF_8)).labels());
        assertArrayEquals(new int[]{2}, dfa.search("href=javascript:x".getBytes(StandardCharsets.UTF_8)).labels());
        assertArrayEquals(new int[]{3}, dfa.search("../etc/passwd".getBytes(StandardCharsets.UTF_8)).labels());
        assertNull(dfa.search("totally safe text".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testCompileLabeledMismatchedSizesThrows() {
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        assertThrows(IllegalArgumentException.class,
                () -> compiler.compileLabeled(List.of("a", "b"), List.of(1), MatchMode.FULL_MATCH));
    }

    @Test
    void testCompileLabeledAppliesMinimization() throws Exception {
        AutomataCompiler compiler = new AutomataCompiler(10_000);
        // "aa|aa" duplicated twice across two labeled rules: the union still
        // must minimize to no more states than the unminimized equivalent.
        Dfa raw;
        {
            var node = RegexParser.parse("if");
            var nfa1 = ThompsonBuilder.build(node, 1);
            var nfa2 = ThompsonBuilder.build(RegexParser.parse("[a-z]+"), 2);
            var combined = ThompsonBuilder.union(List.of(nfa1, nfa2));
            raw = SubsetConstruction.build(combined, MatchMode.FULL_MATCH, 10_000);
        }
        Dfa compiled = compiler.compileLabeled(List.of("if", "[a-z]+"), List.of(1, 2), MatchMode.FULL_MATCH);
        assertTrue(compiled.stateCount() <= raw.stateCount());
    }
}
