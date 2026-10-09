package com.wafgateway.worklet1.automata;

import com.wafgateway.worklet1.regex.RegexNode;

import java.util.List;
import java.util.Map;

/**
 * Builds an {@link Nfa} from a {@link RegexNode} tree using Thompson's
 * construction.
 *
 * <p><b>Theory:</b> Thompson's construction turns each of the five regex forms
 * into a small NFA "fragment" with one start state and one end state, then
 * wires fragments together according to the regex's tree structure. Every
 * fragment adds only O(1) states, so the whole NFA has O(pattern length)
 * states — this is what keeps regex compilation fast (no exponential blowup
 * at this stage; subset construction in Phase 4 is where state counts can grow,
 * which is why that phase enforces a state limit).
 *
 * <p>Fragment diagrams ({@code s} = fragment start, {@code f} = fragment end,
 * {@code ε} = epsilon/no-input transition):
 * <pre>
 * Symbol(set):
 *   s --set--&gt; f
 *
 * Epsilon:
 *   s --ε--&gt; f
 *
 * Concat(A, B):
 *   A.s --...--&gt; A.f --ε--&gt; B.s --...--&gt; B.f
 *   (the whole fragment's start is A.s, end is B.f; no new states needed)
 *
 * Union(A, B, ...):
 *          ε
 *        +---&gt; A.s --...--&gt; A.f ---+
 *        |                          ε
 *   s ---+---&gt; B.s --...--&gt; B.f ---+---&gt; f
 *        |                          ε
 *        +---&gt; ... --------------+
 *
 * Star(A):
 *        +-----------ε-----------+
 *        |                       v
 *   s ---+---&gt; A.s --...--&gt; A.f ---&gt; f
 *        ε                   |  ^
 *                             +--+ (ε, loop back to A.s)
 *                             ε
 * </pre>
 *
 * @author Worklet 1 Team
 */
public final class ThompsonBuilder {

    private ThompsonBuilder() {
        // Utility class; not instantiable.
    }

    /**
     * A fragment under construction: its start and end state ids within the
     * {@link Nfa} being built. The end state is not yet accepting — the caller
     * decides that once the whole tree has been converted.
     */
    private record Fragment(int start, int end) {}

    /**
     * Builds a complete NFA for a single pattern, with its unique accepting
     * state labeled {@code label}.
     *
     * @param node  the parsed regex tree
     * @param label the label to report when this pattern matches
     * @return a new NFA recognizing exactly the language of {@code node}
     */
    public static Nfa build(RegexNode node, int label) {
        Nfa nfa = new Nfa();
        Fragment frag = buildFragment(nfa, node);
        nfa.setStart(frag.start());
        nfa.setAccepting(frag.end(), label);
        return nfa;
    }

    /**
     * Merges several independently-built NFAs (e.g. one per signature rule)
     * into a single NFA: a new start state has an ε-transition to each
     * original NFA's start, and every original accepting state keeps its own
     * label. Running the result finds out *which* of the original patterns (if
     * any) matched.
     *
     * @param nfas the NFAs to merge; each should already have its accepting
     *             state(s) labeled via {@link #build}
     * @return a single NFA equivalent to the union of all inputs
     */
    public static Nfa union(List<Nfa> nfas) {
        Nfa result = new Nfa();
        int newStart = result.newState();

        for (Nfa nfa : nfas) {
            int offset = result.stateCount();
            for (int i = 0; i < nfa.stateCount(); i++) {
                result.newState();
            }
            for (int i = 0; i < nfa.stateCount(); i++) {
                for (int target : nfa.epsilonTargets(i)) {
                    result.addEpsilon(offset + i, offset + target);
                }
                for (Nfa.Edge edge : nfa.edgesFrom(i)) {
                    result.addEdge(offset + i, edge.set(), offset + edge.target());
                }
            }
            result.addEpsilon(newStart, offset + nfa.start());
            for (Map.Entry<Integer, Integer> entry : nfa.acceptingLabels().entrySet()) {
                result.setAccepting(offset + entry.getKey(), entry.getValue());
            }
        }

        result.setStart(newStart);
        return result;
    }

    /**
     * Recursively converts a regex tree node into an NFA fragment, following
     * the five Thompson construction rules (see class Javadoc for diagrams).
     *
     * @param nfa  the NFA being built; new states are added to it
     * @param node the regex node to convert
     * @return the fragment's start and end states
     */
    private static Fragment buildFragment(Nfa nfa, RegexNode node) {
        if (node instanceof RegexNode.Symbol sym) {
            int s = nfa.newState();
            int f = nfa.newState();
            nfa.addEdge(s, sym.set(), f);
            return new Fragment(s, f);
        } else if (node instanceof RegexNode.Epsilon) {
            int s = nfa.newState();
            int f = nfa.newState();
            nfa.addEpsilon(s, f);
            return new Fragment(s, f);
        } else if (node instanceof RegexNode.Concat concat) {
            List<RegexNode> children = concat.children();
            if (children.isEmpty()) {
                int s = nfa.newState();
                int f = nfa.newState();
                nfa.addEpsilon(s, f);
                return new Fragment(s, f);
            }
            Fragment first = buildFragment(nfa, children.get(0));
            int chainEnd = first.end();
            for (int i = 1; i < children.size(); i++) {
                Fragment next = buildFragment(nfa, children.get(i));
                nfa.addEpsilon(chainEnd, next.start());
                chainEnd = next.end();
            }
            return new Fragment(first.start(), chainEnd);
        } else if (node instanceof RegexNode.Union union) {
            int s = nfa.newState();
            int f = nfa.newState();
            for (RegexNode child : union.children()) {
                Fragment frag = buildFragment(nfa, child);
                nfa.addEpsilon(s, frag.start());
                nfa.addEpsilon(frag.end(), f);
            }
            return new Fragment(s, f);
        } else if (node instanceof RegexNode.Star star) {
            int s = nfa.newState();
            int f = nfa.newState();
            Fragment inner = buildFragment(nfa, star.child());
            nfa.addEpsilon(s, inner.start());
            nfa.addEpsilon(s, f);
            nfa.addEpsilon(inner.end(), inner.start());
            nfa.addEpsilon(inner.end(), f);
            return new Fragment(s, f);
        }
        throw new IllegalStateException("Unknown RegexNode type: " + node.getClass());
    }
}
