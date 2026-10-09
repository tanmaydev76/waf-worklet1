package com.wafgateway.worklet1.signature;

import com.wafgateway.worklet1.automata.AutomataCompiler;
import com.wafgateway.worklet1.automata.Dfa;
import com.wafgateway.worklet1.automata.MatchMode;
import com.wafgateway.worklet1.automata.StateLimitExceededException;
import com.wafgateway.worklet1.regex.RegexSyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * A single SEARCH-mode DFA built from the union of every rule in one
 * category (e.g. all {@code SQLI} rules in one machine).
 *
 * <p>One DFA per category — rather than one DFA per rule, or one giant DFA
 * for every rule regardless of category — is a deliberate design choice: it
 * keeps the per-field scanning cost at O(categories × bytes) instead of
 * O(rules × bytes), while still letting {@link SignatureScanner} report
 * which category (and, via the DFA's labels, which specific rule) matched.
 *
 * <p>Labels are the rule's index within this category's own rule list (0,
 * 1, 2, ...), not a global index across all rules — each {@code CategoryDfa}
 * only needs to translate its own DFA's labels back to its own rules.
 *
 * @author Worklet 1 Team
 */
public final class CategoryDfa {

    private final String category;
    private final List<Rule> rules;
    private final Dfa dfa;

    private CategoryDfa(String category, List<Rule> rules, Dfa dfa) {
        this.category = category;
        this.rules = rules;
        this.dfa = dfa;
    }

    /**
     * Builds the DFA for one category from its rules.
     *
     * @param category     the category name
     * @param rulesInGroup every rule belonging to this category, in a fixed
     *                     order (their position here becomes their DFA label)
     * @param compiler     the shared compiler (carries the configured
     *                     {@code dfa.maxStates} limit)
     * @return the compiled, minimized category DFA
     * @throws RegexSyntaxException        if a pattern is malformed (should
     *                                      already have been caught by {@link RuleLoader})
     * @throws StateLimitExceededException if the union of this category's
     *                                      patterns needs too many DFA states
     */
    public static CategoryDfa build(String category, List<Rule> rulesInGroup, AutomataCompiler compiler)
            throws RegexSyntaxException, StateLimitExceededException {
        List<String> patterns = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (int i = 0; i < rulesInGroup.size(); i++) {
            patterns.add(rulesInGroup.get(i).pattern());
            labels.add(i);
        }
        Dfa dfa = compiler.compileLabeled(patterns, labels, MatchMode.SEARCH);
        return new CategoryDfa(category, new ArrayList<>(rulesInGroup), dfa);
    }

    /**
     * Returns the category name.
     *
     * @return the category
     */
    public String category() {
        return category;
    }

    /**
     * Returns the compiled DFA.
     *
     * @return the DFA
     */
    public Dfa dfa() {
        return dfa;
    }

    /**
     * Looks up which rule a DFA label refers to.
     *
     * @param label a label reported by this DFA's {@code search}/{@code longestMatch}
     * @return the corresponding rule
     */
    public Rule ruleForLabel(int label) {
        return rules.get(label);
    }

    /**
     * Returns the DFA's state count, for diagnostics/reporting.
     *
     * @return the state count
     */
    public int stateCount() {
        return dfa.stateCount();
    }
}
