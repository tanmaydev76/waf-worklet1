package com.wafgateway.worklet1.automata;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Converts an {@link Nfa} into an equivalent, deterministic {@link Dfa} via
 * the classical subset construction (a.k.a. the "powerset construction").
 *
 * <p><b>Theory:</b> each DFA state corresponds to a SET of NFA states — "every
 * NFA state the machine could currently be in, given the bytes consumed so
 * far." A DFA has no ambiguity (exactly one transition per byte per state), so
 * running it is O(n) in the input length with no backtracking — the entire
 * reason this project builds its own engine instead of using {@code
 * java.util.regex}, which can backtrack exponentially (ReDoS).
 *
 * <p>Construction is a breadth-first search: starting from the ε-closure of
 * the NFA's start state, for every discovered DFA state and every possible
 * byte (0–255), compute the NFA states reachable by that byte, then ε-close
 * them, to find the next DFA state — assigning each distinct NFA-state-set a
 * new DFA id the first time it is seen. A {@link BitSet} of NFA state ids is
 * used as the de-duplication key: it has well-defined, content-based
 * equals/hashCode (unlike relying on a generic {@code Set}'s implementation
 * details) and stays compact for the NFA sizes this project builds.
 *
 * <p><b>SEARCH mode</b> finds a pattern anywhere in the input, not just as a
 * full match. This is implemented by unioning ε-closure(start) into every
 * computed next-state-set — equivalent to having prefixed the original
 * pattern with {@code .*}, so "start trying to match again from here" is
 * always one of the live possibilities. Because of this, a SEARCH-mode DFA's
 * transition function never actually produces an empty set, so the dedicated
 * {@code dead} state (always present on every {@link Dfa}, per its contract)
 * is reachable only in {@link MatchMode#FULL_MATCH} mode. It is allocated
 * up front with an explicit, fixed self-loop in both modes — computed
 * directly rather than through the generic BFS step, specifically so that
 * the "union ε-closure(start) in" rule can never redirect it away from
 * itself in SEARCH mode.
 *
 * @author Worklet 1 Team
 */
public final class SubsetConstruction {

    private SubsetConstruction() {
        // Utility class; not instantiable.
    }

    /**
     * Builds a DFA equivalent to the given NFA.
     *
     * @param nfa       the source NFA (e.g. from {@code ThompsonBuilder})
     * @param mode      {@link MatchMode#FULL_MATCH} or {@link MatchMode#SEARCH}
     * @param maxStates the maximum number of DFA states allowed; exceeding it
     *                  aborts construction rather than letting a pathological
     *                  pattern consume unbounded memory and time
     * @return the resulting DFA (not yet Hopcroft-minimized)
     * @throws StateLimitExceededException if more than {@code maxStates}
     *                                      distinct DFA states would be needed
     */
    public static Dfa build(Nfa nfa, MatchMode mode, int maxStates) throws StateLimitExceededException {
        Map<BitSet, Integer> idOf = new HashMap<>();
        List<BitSet> setOf = new ArrayList<>();
        List<int[]> transitions = new ArrayList<>();
        List<int[]> labelsPerState = new ArrayList<>();
        Set<Integer> everSeen = new HashSet<>();

        BitSet startSet = toBitSet(nfa.epsilonClosure(Set.of(nfa.start())));
        int startId = intern(startSet, idOf, setOf, transitions, labelsPerState, maxStates);
        everSeen.add(startId);

        BitSet emptySet = new BitSet();
        int deadId = intern(emptySet, idOf, setOf, transitions, labelsPerState, maxStates);
        everSeen.add(deadId);

        // Fill the dead state's row directly: a fixed self-loop on every byte,
        // never accepting. Not computed via the generic BFS step below, so
        // SEARCH mode's "union startSet in" rule can never touch it.
        int[] deadRow = new int[256];
        Arrays.fill(deadRow, deadId);
        transitions.set(deadId, deadRow);
        labelsPerState.set(deadId, new int[0]);

        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(startId);

        while (!queue.isEmpty()) {
            int currentId = queue.poll();
            BitSet currentSet = setOf.get(currentId);
            Set<Integer> currentAsSet = fromBitSet(currentSet);

            int[] row = new int[256];
            for (int b = 0; b < 256; b++) {
                Set<Integer> moved = nfa.move(currentAsSet, b);
                Set<Integer> closureSet = nfa.epsilonClosure(moved);
                BitSet closure = toBitSet(closureSet);
                if (mode == MatchMode.SEARCH) {
                    closure.or(startSet);
                }

                Integer targetId = idOf.get(closure);
                if (targetId == null) {
                    targetId = intern(closure, idOf, setOf, transitions, labelsPerState, maxStates);
                }
                if (everSeen.add(targetId)) {
                    queue.add(targetId);
                }
                row[b] = targetId;
            }

            transitions.set(currentId, row);
            labelsPerState.set(currentId, computeLabels(currentSet, nfa));
        }

        int[][] nextArray = transitions.toArray(new int[0][]);
        int[][] labelsArray = labelsPerState.toArray(new int[0][]);
        return new Dfa(nextArray, startId, deadId, labelsArray);
    }

    /**
     * Registers a new NFA-state-set as a DFA state if it has not been seen
     * before, enforcing {@code maxStates}. Appends placeholder transition and
     * label rows (filled in once the state's actual row is computed by the
     * caller); returns the existing id unchanged if this set is already known.
     */
    private static int intern(BitSet set, Map<BitSet, Integer> idOf, List<BitSet> setOf,
                               List<int[]> transitions, List<int[]> labelsPerState,
                               int maxStates) throws StateLimitExceededException {
        Integer existing = idOf.get(set);
        if (existing != null) {
            return existing;
        }
        int newId = setOf.size();
        if (newId >= maxStates) {
            throw new StateLimitExceededException(
                    "DFA construction exceeded the state limit of " + maxStates
                            + "; the pattern may be too complex, or dfa.maxStates should be raised.");
        }
        idOf.put(set, newId);
        setOf.add(set);
        transitions.add(null);
        labelsPerState.add(null);
        return newId;
    }

    private static BitSet toBitSet(Set<Integer> states) {
        BitSet bs = new BitSet();
        for (int s : states) {
            bs.set(s);
        }
        return bs;
    }

    private static Set<Integer> fromBitSet(BitSet bs) {
        Set<Integer> result = new HashSet<>();
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            result.add(i);
        }
        return result;
    }

    /**
     * Computes a DFA state's labels: the sorted, deduplicated labels of every
     * NFA accepting state contained in its underlying NFA-state-set.
     */
    private static int[] computeLabels(BitSet nfaStates, Nfa nfa) {
        TreeSet<Integer> labels = new TreeSet<>();
        for (Map.Entry<Integer, Integer> entry : nfa.acceptingLabels().entrySet()) {
            if (nfaStates.get(entry.getKey())) {
                labels.add(entry.getValue());
            }
        }
        int[] result = new int[labels.size()];
        int i = 0;
        for (int label : labels) {
            result[i++] = label;
        }
        return result;
    }
}
