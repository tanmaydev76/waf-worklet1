package com.wafgateway.worklet1.signature;

import com.wafgateway.worklet1.automata.AutomataCompiler;
import com.wafgateway.worklet1.automata.Dfa;
import com.wafgateway.worklet1.automata.StateLimitExceededException;
import com.wafgateway.worklet1.regex.RegexSyntaxException;
import com.wafgateway.worklet1.request.Field;
import com.wafgateway.worklet1.request.Normalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans extracted request fields for attack signatures.
 *
 * <p>At construction, groups the given rules by category and compiles one
 * {@link CategoryDfa} per distinct category — so adding a brand-new category
 * to {@code rules.txt} (e.g. a future {@code LOG4SHELL} category) needs no
 * code change here: the grouping is driven entirely by whatever category
 * strings the loaded rules contain.
 *
 * <p>{@link #scan} normalizes each field's text once, then runs every
 * category DFA's {@code search} against it — total cost O(categories ×
 * bytes), not O(rules × bytes), since each category's rules were already
 * merged into a single DFA by {@link CategoryDfa}. At most one match is
 * recorded per (field, category) pair, even if that category has several
 * rules — {@code search} already stops at the first hit, and if more than
 * one rule in the category matches at that exact position, the
 * lowest-numbered rule is reported (an arbitrary but deterministic tie-break).
 *
 * @author Worklet 1 Team
 */
public final class SignatureScanner {

    private final List<CategoryDfa> categoryDfas;

    /**
     * Builds a scanner from a flat rule list, grouping by category and
     * compiling each group's DFA immediately (so a bad pattern fails at
     * construction time, not on the first scan).
     *
     * @param rules     every loaded rule, from any/all categories
     * @param maxStates the DFA state limit to enforce per category
     * @throws RegexSyntaxException        if a pattern is malformed
     * @throws StateLimitExceededException if a category's combined pattern
     *                                      needs too many DFA states
     */
    public SignatureScanner(List<Rule> rules, int maxStates) throws RegexSyntaxException, StateLimitExceededException {
        Map<String, List<Rule>> byCategory = new LinkedHashMap<>();
        for (Rule rule : rules) {
            byCategory.computeIfAbsent(rule.category(), k -> new ArrayList<>()).add(rule);
        }

        AutomataCompiler compiler = new AutomataCompiler(maxStates);
        List<CategoryDfa> dfas = new ArrayList<>();
        for (Map.Entry<String, List<Rule>> entry : byCategory.entrySet()) {
            dfas.add(CategoryDfa.build(entry.getKey(), entry.getValue(), compiler));
        }
        this.categoryDfas = dfas;
    }

    /**
     * Scans every field for attack signatures.
     *
     * @param fields the extracted fields (see {@code FieldExtractor})
     * @return every match found, in field order then category order
     */
    public List<Match> scan(List<Field> fields) {
        List<Match> matches = new ArrayList<>();
        for (Field field : fields) {
            boolean isQuery = field.name().startsWith("query.");
            byte[] normalized = Normalizer.normalize(field.value(), isQuery);

            for (CategoryDfa categoryDfa : categoryDfas) {
                Dfa.SearchHit hit = categoryDfa.dfa().search(normalized);
                if (hit != null) {
                    int label = Arrays.stream(hit.labels()).min().orElseThrow();
                    Rule rule = categoryDfa.ruleForLabel(label);
                    matches.add(new Match(rule.id(), rule.category(), rule.severity(), field.name(), hit.position()));
                }
            }
        }
        return matches;
    }

    /**
     * Returns the compiled category DFAs, for diagnostics/reporting (e.g.
     * printing each category's state count).
     *
     * @return the category DFAs, in category-discovery order
     */
    public List<CategoryDfa> categoryDfas() {
        return categoryDfas;
    }
}
