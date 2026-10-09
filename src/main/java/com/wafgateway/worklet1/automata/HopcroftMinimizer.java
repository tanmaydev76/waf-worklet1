package com.wafgateway.worklet1.automata;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimizes a DFA using Hopcroft's algorithm.
 *
 * <p><b>Theory — state equivalence, in plain words:</b> two DFA states are
 * "equivalent" if, no matter what input you feed the machine from that point
 * on, you get exactly the same outcome (matched or not, and with the same
 * labels) whichever of the two states you started from. If two states are
 * equivalent, one of them is redundant — the machine can be rebuilt with
 * them merged into a single state, and it will still recognize exactly the
 * same language. The Myhill–Nerode theorem says that for a regular language
 * there is exactly one DFA (up to renaming states) with the fewest possible
 * states, and it is obtained by merging every pair of equivalent states.
 * Minimizing the DFA keeps the engine's memory footprint and {@code toDot()}
 * diagrams as small as possible without changing what the pattern matches.
 *
 * <p><b>Finding equivalent states — the refinement idea:</b> start by
 * assuming states are equivalent unless proven otherwise, grouped only by
 * the most obvious distinguishing fact available up front: their accepting
 * labels (two states that report different rules, or one that accepts and
 * one that doesn't, obviously cannot be equivalent). Then repeatedly look
 * for a reason to split a group further: if some byte sends part of a group
 * into states we have already decided belong to different groups, and the
 * rest of the group into another, then that byte actually *does*
 * distinguish those states — split the group along that line. Keep
 * splitting until no more splits are found; whatever groups remain are
 * exactly the equivalence classes.
 *
 * <p><b>Hopcroft's refinement, mechanically:</b> a worklist holds groups
 * still suspected of containing a splittable pair. Pop a group {@code A};
 * for every byte {@code c}, compute the set {@code X} of states that
 * transition into {@code A} on {@code c} (using precomputed inverse
 * transitions, so this doesn't require scanning every state). Any existing
 * group {@code Y} that {@code X} only partially overlaps gets split into
 * {@code Y ∩ X} and {@code Y \ X}. The classic efficiency trick ("add the
 * smaller half"): if {@code Y} wasn't already pending in the worklist, only
 * the smaller of the two new pieces needs to be added — the larger piece is
 * still fully accounted for as the (now smaller) remainder of {@code Y}, and
 * revisiting it would just redo work already implied by having checked
 * {@code Y} before. This one rule is what makes the algorithm O(n log n)
 * instead of O(n²): a state can only ever be part of the *smaller* half of
 * a split O(log n) times in total (each time, the group containing it has
 * shrunk by at least half), bounding the total work across the whole run.
 *
 * @author Worklet 1 Team
 */
public final class HopcroftMinimizer {

    private HopcroftMinimizer() {
        // Utility class; not instantiable.
    }

    /**
     * Returns the smallest DFA equivalent to {@code dfa}: same language,
     * same labels on every accepted input, but no two states that could be
     * merged without changing behavior.
     *
     * @param dfa the DFA to minimize
     * @return an equivalent DFA with the fewest possible states, renumbered
     *         with the start state as id 0 in BFS order, and a dead state present
     */
    public static Dfa minimize(Dfa dfa) {
        int n = dfa.stateCount();

        Partition partition = initialPartitionByLabel(dfa, n);
        refine(dfa, n, partition);

        return rebuild(dfa, partition);
    }

    /**
     * Tracks the current partition of states into blocks, both directions:
     * {@code blockOf[state]} for O(1) lookup of a state's current block, and
     * {@code members.get(blockId)} for enumerating a block's states. Retired
     * blocks (replaced by a split) have their {@code members} entry set to
     * null; their id is never reused.
     */
    private static final class Partition {
        final int[] blockOf;
        final List<Set<Integer>> members = new ArrayList<>();

        Partition(int n) {
            blockOf = new int[n];
        }

        int newBlock(Set<Integer> states) {
            int id = members.size();
            members.add(states);
            for (int s : states) {
                blockOf[s] = id;
            }
            return id;
        }
    }

    /**
     * Builds the initial partition: states with the exact same label set
     * (same rules would report, including "no rules" for non-accepting
     * states) start in the same block. Refinement below can only ever split
     * these groups further, never merge across them — so states with
     * different labels are guaranteed to never end up equivalent.
     */
    private static Partition initialPartitionByLabel(Dfa dfa, int n) {
        Partition partition = new Partition(n);
        Map<List<Integer>, Set<Integer>> groups = new HashMap<>();
        for (int s = 0; s < n; s++) {
            List<Integer> key = new ArrayList<>();
            for (int label : dfa.labelsOf(s)) {
                key.add(label);
            }
            groups.computeIfAbsent(key, k -> new HashSet<>()).add(s);
        }
        for (Set<Integer> group : groups.values()) {
            partition.newBlock(group);
        }
        return partition;
    }

    /**
     * Runs Hopcroft's worklist refinement to completion, splitting blocks of
     * {@code partition} until no more splits are possible.
     */
    private static void refine(Dfa dfa, int n, Partition partition) {
        // predecessors[c][s] = every state p with dfa.step(p, c) == s
        List<List<Integer>>[] predecessors = buildInversePredecessors(dfa, n);

        Deque<Integer> worklist = new ArrayDeque<>();
        List<Boolean> inWorklist = new ArrayList<>();
        for (int id = 0; id < partition.members.size(); id++) {
            inWorklist.add(true);
            worklist.add(id);
        }

        while (!worklist.isEmpty()) {
            int aId = worklist.poll();
            if (aId >= inWorklist.size() || !inWorklist.get(aId) || partition.members.get(aId) == null) {
                continue; // stale: already retired by a split since being enqueued
            }
            inWorklist.set(aId, false);
            Set<Integer> aMembers = partition.members.get(aId);

            for (int c = 0; c < 256; c++) {
                Set<Integer> x = new HashSet<>();
                for (int s : aMembers) {
                    x.addAll(predecessors[c].get(s));
                }
                if (x.isEmpty()) {
                    continue;
                }

                Set<Integer> touchedBlocks = new HashSet<>();
                for (int p : x) {
                    touchedBlocks.add(partition.blockOf[p]);
                }

                for (int yId : touchedBlocks) {
                    Set<Integer> yMembers = partition.members.get(yId);
                    if (yMembers == null) {
                        continue;
                    }

                    Set<Integer> yIntersectX = new HashSet<>();
                    Set<Integer> yMinusX = new HashSet<>();
                    for (int state : yMembers) {
                        if (x.contains(state)) {
                            yIntersectX.add(state);
                        } else {
                            yMinusX.add(state);
                        }
                    }
                    if (yIntersectX.isEmpty() || yMinusX.isEmpty()) {
                        continue; // X does not actually split Y
                    }

                    boolean yWasPending = yId < inWorklist.size() && inWorklist.get(yId);

                    partition.members.set(yId, null); // retire
                    int id1 = partition.newBlock(yIntersectX);
                    int id2 = partition.newBlock(yMinusX);
                    while (inWorklist.size() <= id2) {
                        inWorklist.add(false);
                    }

                    if (yWasPending) {
                        inWorklist.set(id1, true);
                        inWorklist.set(id2, true);
                        worklist.add(id1);
                        worklist.add(id2);
                    } else if (yIntersectX.size() <= yMinusX.size()) {
                        inWorklist.set(id1, true);
                        worklist.add(id1);
                    } else {
                        inWorklist.set(id2, true);
                        worklist.add(id2);
                    }
                }
            }
        }
    }

    private static List<List<Integer>>[] buildInversePredecessors(Dfa dfa, int n) {
        @SuppressWarnings("unchecked")
        List<List<Integer>>[] predecessors = new List[256];
        for (int c = 0; c < 256; c++) {
            predecessors[c] = new ArrayList<>();
            for (int s = 0; s < n; s++) {
                predecessors[c].add(new ArrayList<>());
            }
        }
        for (int p = 0; p < n; p++) {
            for (int c = 0; c < 256; c++) {
                int t = dfa.step(p, c);
                predecessors[c].get(t).add(p);
            }
        }
        return predecessors;
    }

    /**
     * Rebuilds a DFA from the final partition: one new state per surviving
     * block, transitions and labels copied from an arbitrary representative
     * member of each block (guaranteed identical across the whole block,
     * since that is exactly what "equivalent" means). States are numbered in
     * BFS order starting from the block containing the original start state,
     * so the new start is always id 0. The block containing the original
     * dead state is included even if it turns out to be unreachable (as
     * happens for a {@link MatchMode#SEARCH}-mode DFA, whose dead state is
     * allocated but never actually transitioned into), so {@code dead} is
     * always a valid state on the result, matching every {@link Dfa}'s contract.
     */
    private static Dfa rebuild(Dfa dfa, Partition partition) {
        int startBlock = partition.blockOf[dfa.start()];
        int deadBlock = partition.blockOf[dfa.dead()];

        Map<Integer, Integer> newIdOf = new HashMap<>();
        List<Integer> blockByNewId = new ArrayList<>();

        newIdOf.put(startBlock, 0);
        blockByNewId.add(startBlock);
        Deque<Integer> bfs = new ArrayDeque<>();
        bfs.add(startBlock);

        while (!bfs.isEmpty()) {
            int block = bfs.poll();
            int representative = anyMember(partition, block);
            for (int c = 0; c < 256; c++) {
                int targetBlock = partition.blockOf[dfa.step(representative, c)];
                if (!newIdOf.containsKey(targetBlock)) {
                    newIdOf.put(targetBlock, blockByNewId.size());
                    blockByNewId.add(targetBlock);
                    bfs.add(targetBlock);
                }
            }
        }

        if (!newIdOf.containsKey(deadBlock)) {
            newIdOf.put(deadBlock, blockByNewId.size());
            blockByNewId.add(deadBlock);
        }

        int count = blockByNewId.size();
        int[][] newNext = new int[count][256];
        int[][] newLabels = new int[count][];

        for (int newId = 0; newId < count; newId++) {
            int block = blockByNewId.get(newId);
            int representative = anyMember(partition, block);
            for (int c = 0; c < 256; c++) {
                int targetBlock = partition.blockOf[dfa.step(representative, c)];
                newNext[newId][c] = newIdOf.get(targetBlock);
            }
            newLabels[newId] = dfa.labelsOf(representative);
        }

        int newStart = newIdOf.get(startBlock);
        int newDead = newIdOf.get(deadBlock);
        return new Dfa(newNext, newStart, newDead, newLabels);
    }

    private static int anyMember(Partition partition, int block) {
        return partition.members.get(block).iterator().next();
    }
}
