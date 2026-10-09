package com.wafgateway.worklet1.automata;

import com.wafgateway.worklet1.regex.ByteSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A non-deterministic finite automaton (NFA) over the byte alphabet 0–255.
 *
 * <p><b>Theory:</b> An NFA is a 5-tuple (Q, Σ, δ, q0, F) where Q is a finite set
 * of states, Σ = {0..255} is the alphabet, δ is a transition relation (a state
 * and a byte may lead to several possible next states, or via ε to another
 * state without consuming input), q0 is the start state, and F is the set of
 * accepting states. Unlike a DFA, an NFA may be in several states "at once";
 * matching against an NFA directly (via {@link #simulate}) therefore tracks a
 * *set* of states and can be exponential in the worst case. Production request
 * scanning never runs on the NFA directly — {@code SubsetConstruction} (Phase 4)
 * converts it to an equivalent DFA with guaranteed O(n) matching. The NFA
 * simulation here exists purely so tests can verify Thompson's construction
 * independently of the later DFA machinery.
 *
 * <p>Each accepting state carries an integer label, so that a single NFA built
 * from the union of many rule patterns can report *which* rule matched.
 *
 * @author Worklet 1 Team
 */
public final class Nfa {
    private final List<List<Integer>> epsilonTransitions = new ArrayList<>();
    private final List<List<Edge>> byteTransitions = new ArrayList<>();
    private final Map<Integer, Integer> acceptingLabels = new HashMap<>();
    private int start;

    /**
     * A single byte-consuming transition: on any byte in {@code set}, move to
     * {@code target}.
     *
     * @param set    the set of bytes that trigger this transition
     * @param target the destination state
     */
    public record Edge(ByteSet set, int target) {}

    /**
     * Creates a new state and returns its id.
     *
     * <p>States are numbered 0, 1, 2, ... in creation order.
     *
     * @return the id of the newly created state
     */
    int newState() {
        epsilonTransitions.add(new ArrayList<>());
        byteTransitions.add(new ArrayList<>());
        return epsilonTransitions.size() - 1;
    }

    /**
     * Adds an ε-transition (no byte consumed) from {@code from} to {@code to}.
     *
     * @param from source state
     * @param to   destination state
     */
    void addEpsilon(int from, int to) {
        epsilonTransitions.get(from).add(to);
    }

    /**
     * Adds a byte-consuming transition from {@code from} to {@code to} on any
     * byte in {@code set}.
     *
     * @param from source state
     * @param set  the triggering byte set
     * @param to   destination state
     */
    void addEdge(int from, ByteSet set, int to) {
        byteTransitions.get(from).add(new Edge(set, to));
    }

    /**
     * Sets the start state of this NFA.
     *
     * @param state the state to use as the start
     */
    void setStart(int state) {
        this.start = state;
    }

    /**
     * Marks {@code state} as accepting with the given rule label.
     *
     * @param state the state to mark
     * @param label the label to report when this state is reached
     */
    void setAccepting(int state, int label) {
        acceptingLabels.put(state, label);
    }

    /**
     * Returns the start state.
     *
     * @return the id of the start state
     */
    public int start() {
        return start;
    }

    /**
     * Returns the number of states in this NFA.
     *
     * @return the state count
     */
    public int stateCount() {
        return epsilonTransitions.size();
    }

    /**
     * Returns the ε-transition targets from the given state.
     *
     * @param state the source state
     * @return an unmodifiable list of destination states
     */
    public List<Integer> epsilonTargets(int state) {
        return Collections.unmodifiableList(epsilonTransitions.get(state));
    }

    /**
     * Returns the byte-consuming edges from the given state.
     *
     * @param state the source state
     * @return an unmodifiable list of edges
     */
    public List<Edge> edgesFrom(int state) {
        return Collections.unmodifiableList(byteTransitions.get(state));
    }

    /**
     * Returns the map of accepting states to their rule labels.
     *
     * @return an unmodifiable view of state → label
     */
    public Map<Integer, Integer> acceptingLabels() {
        return Collections.unmodifiableMap(acceptingLabels);
    }

    /**
     * Computes the ε-closure of a set of states: all states reachable from the
     * given states using only ε-transitions (including the given states
     * themselves). Implemented with a worklist (DFS via an explicit stack) in
     * O(states + transitions).
     *
     * @param states the starting set of states
     * @return the ε-closure, as a new mutable set
     */
    public Set<Integer> epsilonClosure(Collection<Integer> states) {
        Set<Integer> closure = new HashSet<>(states);
        Deque<Integer> worklist = new ArrayDeque<>(states);
        while (!worklist.isEmpty()) {
            int s = worklist.pop();
            for (int t : epsilonTransitions.get(s)) {
                if (closure.add(t)) {
                    worklist.push(t);
                }
            }
        }
        return closure;
    }

    /**
     * Computes the set of states reachable from {@code states} by consuming
     * exactly one byte, WITHOUT taking the ε-closure afterward (callers that
     * need the full "move" step, as subset construction does, must call
     * {@link #epsilonClosure} on the result themselves).
     *
     * @param states    the current set of states
     * @param byteValue the byte to consume (0–255)
     * @return the set of states reachable on that byte
     */
    public Set<Integer> move(Collection<Integer> states, int byteValue) {
        Set<Integer> next = new HashSet<>();
        for (int s : states) {
            for (Edge edge : byteTransitions.get(s)) {
                if (edge.set().contains(byteValue)) {
                    next.add(edge.target());
                }
            }
        }
        return next;
    }

    /**
     * Simulates this NFA on an input, for testing only.
     *
     * <p><b>Theory:</b> maintains the set of all NFA states reachable after
     * consuming the bytes seen so far — the same "set of states" idea subset
     * construction uses to build a DFA, but computed on demand instead of
     * precomputed into a table. Matching is a full match: the input must be
     * entirely consumed and land on at least one accepting state.
     *
     * <p>This is for test verification of Thompson's construction only.
     * Production scanning always goes through a compiled {@code Dfa}, which
     * guarantees O(n) time; this method can be exponential in the worst case
     * because the reachable state set can grow combinatorially.
     *
     * @param input the bytes to run the NFA on
     * @return the sorted set of labels of all accepting states reached after
     *         consuming all of {@code input}; empty if the input is rejected
     */
    public SortedSet<Integer> simulate(byte[] input) {
        Set<Integer> current = epsilonClosure(Set.of(start));
        for (byte b : input) {
            int unsignedByte = b & 0xFF;
            Set<Integer> moved = move(current, unsignedByte);
            current = epsilonClosure(moved);
        }
        SortedSet<Integer> labels = new TreeSet<>();
        for (int state : current) {
            Integer label = acceptingLabels.get(state);
            if (label != null) {
                labels.add(label);
            }
        }
        return labels;
    }
}
