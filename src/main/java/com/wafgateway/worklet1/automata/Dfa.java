package com.wafgateway.worklet1.automata;

import com.wafgateway.worklet1.regex.ByteSet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A deterministic finite automaton (DFA) over the byte alphabet 0–255.
 *
 * <p><b>Theory:</b> unlike an {@link Nfa}, a DFA has exactly one transition
 * per (state, byte) pair, so running it on an input of length n takes exactly
 * n transition lookups — O(n) with no backtracking, no matter how the
 * pattern is written. This guarantee (not available from {@code
 * java.util.regex}, which can backtrack exponentially — see {@code
 * RedosDemo}) is the entire reason this project builds its own engine.
 *
 * <p>Represented as a dense transition table {@code next[state][byte]} rather
 * than a sparse map, since the byte alphabet is small and fixed (256) and a
 * dense table makes every runtime method a simple array lookup.
 *
 * <p>Every DFA has a designated {@code dead} state: non-accepting, and
 * self-looping on every byte. Once a match attempt reaches it, no further
 * input can ever make it accepting again, so runtime methods stop early
 * there. (In a {@link MatchMode#SEARCH}-mode DFA this state is allocated but
 * normally unreachable — see {@link SubsetConstruction}.)
 *
 * @author Worklet 1 Team
 */
public final class Dfa {
    private final int[][] next;
    private final int start;
    private final int dead;
    private final int[][] labels;

    /**
     * The result of {@link #search}: the first position where a match was
     * found, and the labels of the rule(s) that matched there.
     *
     * @param position the number of input bytes consumed (from the start of
     *                  the input) when an accepting state was first reached —
     *                  i.e. the exclusive end offset of the earliest match
     *                  found while scanning left to right. (This engine does
     *                  not separately track where that match started.)
     * @param labels    the sorted, deduplicated labels of the rule(s) that matched
     */
    public record SearchHit(int position, int[] labels) {}

    /**
     * The result of {@link #longestMatch}: how far the longest accepted
     * prefix extends, and which rule(s) it satisfies.
     *
     * @param endIndex the exclusive end index in the input (so the matched
     *                  text is {@code input[from, endIndex)})
     * @param labels   the sorted, deduplicated labels of the rule(s) satisfied
     *                  by that prefix
     */
    public record LongestMatch(int endIndex, int[] labels) {}

    /**
     * Package-private: DFAs are only ever produced by {@link SubsetConstruction}
     * (or, later, a minimizer that rebuilds an equivalent smaller DFA), never
     * constructed directly by callers.
     *
     * @param next   dense transition table, {@code next[state][byte & 0xFF]}
     * @param start  the start state
     * @param dead   the non-accepting, self-looping dead state
     * @param labels per-state sorted label arrays; empty means non-accepting
     */
    Dfa(int[][] next, int start, int dead, int[][] labels) {
        this.next = next;
        this.start = start;
        this.dead = dead;
        this.labels = labels;
    }

    /**
     * Returns the start state. Package-private: external callers use
     * {@link #fullMatch}, {@link #search}, or {@link #longestMatch}, which
     * already begin from it; this exists for same-package tooling (tests,
     * and a future minimizer that needs to inspect/rebuild this DFA).
     *
     * @return the start state
     */
    int start() {
        return start;
    }

    /**
     * Returns the dead state. Package-private for the same reason as
     * {@link #start()}.
     *
     * @return the dead state
     */
    int dead() {
        return dead;
    }

    /**
     * Looks up the single next state for one byte. O(1).
     *
     * @param state the current state
     * @param b     the byte to consume (only the low 8 bits are used)
     * @return the next state
     */
    public int step(int state, int b) {
        return next[state][b & 0xFF];
    }

    /**
     * Checks whether the entire input matches the pattern. O(n), one pass,
     * stops immediately if the dead state is reached.
     *
     * @param input the bytes to match
     * @return true if the whole input is accepted
     */
    public boolean fullMatch(byte[] input) {
        int state = start;
        for (byte b : input) {
            state = step(state, b);
            if (state == dead) {
                return false;
            }
        }
        return labels[state].length > 0;
    }

    /**
     * Finds the first point in the input where the pattern matches, scanning
     * left to right. Intended for use with a {@link MatchMode#SEARCH}-mode
     * DFA, whose construction already makes "start trying again from here"
     * part of every state, so this never needs to restart the scan manually.
     * O(n), one pass, returns as soon as a match is found.
     *
     * @param input the bytes to scan
     * @return the first hit, or null if the pattern never matches
     */
    public SearchHit search(byte[] input) {
        int state = start;
        if (labels[state].length > 0) {
            return new SearchHit(0, labels[state]);
        }
        for (int i = 0; i < input.length; i++) {
            state = step(state, input[i]);
            if (state == dead) {
                return null;
            }
            if (labels[state].length > 0) {
                return new SearchHit(i + 1, labels[state]);
            }
        }
        return null;
    }

    /**
     * Finds the longest prefix of {@code input}, starting at {@code from},
     * that the pattern accepts ("maximal munch" — used by the tokenizer to
     * greedily consume one token at a time). O(n), one pass from {@code from},
     * stops immediately once the dead state is reached since nothing further
     * could ever become accepting again.
     *
     * @param input the bytes to scan
     * @param from  the index to start scanning from
     * @return the longest accepted prefix's end index and labels, or null if
     *         no prefix starting at {@code from} (including the empty one) is accepted
     */
    public LongestMatch longestMatch(byte[] input, int from) {
        int state = start;
        int bestEnd = -1;
        int[] bestLabels = null;

        if (labels[state].length > 0) {
            bestEnd = from;
            bestLabels = labels[state];
        }

        for (int i = from; i < input.length; i++) {
            state = step(state, input[i]);
            if (state == dead) {
                break;
            }
            if (labels[state].length > 0) {
                bestEnd = i + 1;
                bestLabels = labels[state];
            }
        }

        if (bestLabels == null) {
            return null;
        }
        return new LongestMatch(bestEnd, bestLabels);
    }

    /**
     * Returns the number of states in this DFA.
     *
     * @return the state count
     */
    public int stateCount() {
        return next.length;
    }

    /**
     * Renders this DFA as Graphviz DOT source, for visualization/debugging.
     * The dead state is omitted (it adds no useful information — every state
     * transitions there on whatever bytes aren't shown). Parallel edges to
     * the same target are merged into a single edge, labeled with the union
     * of their triggering bytes via {@link ByteSet#describe}.
     *
     * @return DOT-format source describing this DFA
     */
    public String toDot() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph DFA {\n");
        sb.append("  rankdir=LR;\n");

        for (int s = 0; s < next.length; s++) {
            if (s == dead) {
                continue;
            }
            String shape = labels[s].length > 0 ? "doublecircle" : "circle";
            sb.append("  ").append(s).append(" [shape=").append(shape).append("];\n");
        }

        sb.append("  __start__ [shape=point];\n");
        sb.append("  __start__ -> ").append(start).append(";\n");

        for (int s = 0; s < next.length; s++) {
            if (s == dead) {
                continue;
            }
            // Group bytes by target state so parallel edges collapse into one.
            Map<Integer, ByteSet> grouped = new LinkedHashMap<>();
            for (int b = 0; b < 256; b++) {
                int target = next[s][b];
                if (target == dead) {
                    continue;
                }
                grouped.merge(target, ByteSet.of(b), ByteSet::union);
            }
            for (Map.Entry<Integer, ByteSet> entry : grouped.entrySet()) {
                sb.append("  ").append(s).append(" -> ").append(entry.getKey())
                        .append(" [label=\"").append(escapeDotLabel(entry.getValue().describe())).append("\"];\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String escapeDotLabel(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
