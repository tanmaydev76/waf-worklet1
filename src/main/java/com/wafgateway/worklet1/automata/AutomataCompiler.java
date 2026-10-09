package com.wafgateway.worklet1.automata;

import com.wafgateway.worklet1.regex.RegexNode;
import com.wafgateway.worklet1.regex.RegexParser;
import com.wafgateway.worklet1.regex.RegexSyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience facade that runs the whole regex-to-DFA pipeline in one call:
 * {@code parse → Thompson's construction → (union, if multiple patterns) →
 * subset construction → Hopcroft minimization}.
 *
 * <p>Patterns compiled here always use {@code ignoreCase = false}: attack
 * signatures are matched against request text that the {@code Normalizer}
 * has already lowercased, so case-folding the pattern itself would be
 * redundant.
 *
 * @author Worklet 1 Team
 */
public final class AutomataCompiler {

    private final int maxStates;

    /**
     * Creates a compiler that enforces the given DFA state limit on every
     * pattern it compiles (see {@link StateLimitExceededException}).
     *
     * @param maxStates the maximum number of DFA states allowed per compiled pattern
     */
    public AutomataCompiler(int maxStates) {
        this.maxStates = maxStates;
    }

    /**
     * Compiles a single pattern into a minimized DFA, labeled 0.
     *
     * @param pattern the regex pattern text
     * @param mode    FULL_MATCH or SEARCH
     * @return the compiled, minimized DFA
     * @throws RegexSyntaxException        if the pattern is malformed
     * @throws StateLimitExceededException if the pattern needs more than
     *                                      {@code maxStates} DFA states
     */
    public Dfa compile(String pattern, MatchMode mode) throws RegexSyntaxException, StateLimitExceededException {
        RegexNode node = RegexParser.parse(pattern, false);
        Nfa nfa = ThompsonBuilder.build(node, 0);
        Dfa dfa = SubsetConstruction.build(nfa, mode, maxStates);
        return HopcroftMinimizer.minimize(dfa);
    }

    /**
     * Compiles several patterns into a single minimized DFA that reports
     * which pattern(s) matched via labels — e.g. one DFA per signature
     * category, built from the union of that category's rules.
     *
     * @param patterns the regex pattern texts, one per rule
     * @param labels   the label to report for each corresponding pattern
     *                 (typically the rule's index); must be the same size as
     *                 {@code patterns}
     * @param mode     FULL_MATCH or SEARCH
     * @return the compiled, minimized DFA recognizing the union of all patterns
     * @throws RegexSyntaxException        if any pattern is malformed
     * @throws StateLimitExceededException if the combined pattern needs more
     *                                      than {@code maxStates} DFA states
     */
    public Dfa compileLabeled(List<String> patterns, List<Integer> labels, MatchMode mode)
            throws RegexSyntaxException, StateLimitExceededException {
        if (patterns.size() != labels.size()) {
            throw new IllegalArgumentException(
                    "patterns and labels must have the same size, got "
                            + patterns.size() + " and " + labels.size());
        }
        List<Nfa> nfas = new ArrayList<>();
        for (int i = 0; i < patterns.size(); i++) {
            RegexNode node = RegexParser.parse(patterns.get(i), false);
            nfas.add(ThompsonBuilder.build(node, labels.get(i)));
        }
        Nfa combined = ThompsonBuilder.union(nfas);
        Dfa dfa = SubsetConstruction.build(combined, mode, maxStates);
        return HopcroftMinimizer.minimize(dfa);
    }
}
