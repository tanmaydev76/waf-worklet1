package com.wafgateway.worklet1.regex;

import java.util.List;

/**
 * A node in a regular expression abstract syntax tree (AST).
 *
 * <p><b>Theory:</b> A regular expression is formally defined over an alphabet as:
 * <pre>
 *   r := ∅ | ε | a | r | r | r r | r*
 * </pre>
 * (empty set, epsilon, single symbol, union, concatenation, Kleene star).
 *
 * <p>This sealed interface represents the tree form after parsing and desugaring.
 * Desugar rules simplify the concrete syntax into these five forms:
 * <ul>
 *   <li>{@code r+ → r r*}</li>
 *   <li>{@code r? → r | ε}</li>
 *   <li>{@code r{m,n} → m copies + (n−m) copies of (r | ε)}</li>
 * </ul>
 *
 * @author Worklet 1 Team
 */
public sealed interface RegexNode {

    /**
     * A single symbol (character or character class).
     *
     * @param set the set of bytes that match this symbol
     */
    record Symbol(ByteSet set) implements RegexNode {
        /**
         * Validates that the set is not null.
         */
        public Symbol {
            if (set == null) throw new IllegalArgumentException("set must not be null");
        }
    }

    /**
     * The epsilon (empty string) transition.
     */
    record Epsilon() implements RegexNode {}

    /**
     * Concatenation of zero or more sub-expressions.
     *
     * @param children the sub-expressions, concatenated in order (left to right)
     */
    record Concat(List<RegexNode> children) implements RegexNode {
        /**
         * Validates that children is not null.
         */
        public Concat {
            if (children == null) throw new IllegalArgumentException("children must not be null");
        }
    }

    /**
     * Union (alternation) of zero or more sub-expressions.
     *
     * @param children the sub-expressions; input matches if any child matches
     */
    record Union(List<RegexNode> children) implements RegexNode {
        /**
         * Validates that children is not null.
         */
        public Union {
            if (children == null) throw new IllegalArgumentException("children must not be null");
        }
    }

    /**
     * Kleene star (zero or more repetitions).
     *
     * @param child the expression to repeat
     */
    record Star(RegexNode child) implements RegexNode {
        /**
         * Validates that child is not null.
         */
        public Star {
            if (child == null) throw new IllegalArgumentException("child must not be null");
        }
    }

    /**
     * Returns a pretty-printed tree representation of this node.
     *
     * <p>Useful for debugging and visualization.
     *
     * @return a multi-line string showing the tree structure with indentation
     */
    default String toTree() {
        return toTreeHelper(0);
    }

    /**
     * Helper for tree printing with indentation.
     */
    private String toTreeHelper(int depth) {
        String indent = "  ".repeat(depth);
        if (this instanceof Symbol sym) {
            return indent + "Symbol(" + sym.set().describe() + ")";
        } else if (this instanceof Epsilon) {
            return indent + "Epsilon";
        } else if (this instanceof Concat c) {
            StringBuilder sb = new StringBuilder(indent + "Concat[\n");
            for (RegexNode child : c.children()) {
                sb.append(child.toTreeHelper(depth + 1)).append("\n");
            }
            sb.append(indent + "]");
            return sb.toString();
        } else if (this instanceof Union u) {
            StringBuilder sb = new StringBuilder(indent + "Union[\n");
            for (RegexNode child : u.children()) {
                sb.append(child.toTreeHelper(depth + 1)).append("\n");
            }
            sb.append(indent + "]");
            return sb.toString();
        } else if (this instanceof Star s) {
            return indent + "Star[\n" + s.child().toTreeHelper(depth + 1) + "\n" + indent + "]";
        }
        return indent + "Unknown";
    }
}
