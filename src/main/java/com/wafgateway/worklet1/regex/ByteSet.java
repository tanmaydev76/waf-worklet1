package com.wafgateway.worklet1.regex;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable, O(1) set of bytes 0–255 represented as a bit vector.
 *
 * <p><b>Theory:</b> A byte set is used in a regular expression to represent
 * character classes (e.g., {@code [0-9]}, {@code \d}, {@code [^abc]}). By storing
 * the set as four 64-bit longs (256 bits total), membership tests and set operations
 * run in constant time, enabling efficient DFA construction.
 *
 * <p><b>Implementation:</b> Each byte {@code b (0–255)} is stored as bit
 * {@code b % 64} in word {@code b / 64}. All operations (union, intersect, complement,
 * etc.) work on the long array and return a new immutable ByteSet.
 *
 * @author Worklet 1 Team
 */
public final class ByteSet {
    private static final int WORD_COUNT = 4;
    private static final int BITS_PER_WORD = 64;

    private final long[] words;

    /** The empty set (no bytes). */
    public static final ByteSet EMPTY = new ByteSet(new long[WORD_COUNT]);

    /** The universal set (all bytes 0–255). */
    public static final ByteSet ALL = new ByteSet(new long[]{-1L, -1L, -1L, -1L});

    /** Digits 0–9. */
    public static final ByteSet DIGIT = range('0', '9');

    /** Word characters: a-z, A-Z, 0-9, underscore. */
    public static final ByteSet WORD = range('a', 'z').union(range('A', 'Z'))
            .union(range('0', '9')).union(of('_'));

    /** Whitespace: space, tab, newline, carriage return, form feed, vertical tab. */
    public static final ByteSet SPACE = of(' ', '\t', '\n', '\r', '\f', 11);

    /**
     * Creates a ByteSet from the given long array.
     * Internal use only; assumes the array is valid.
     */
    private ByteSet(long[] words) {
        this.words = words;
    }

    /**
     * Creates a ByteSet containing the specified bytes.
     *
     * @param bytes the bytes to include
     * @return a new ByteSet containing those bytes
     */
    public static ByteSet of(int... bytes) {
        long[] words = new long[WORD_COUNT];
        for (int b : bytes) {
            if (b < 0 || b > 255) {
                throw new IllegalArgumentException("Byte must be 0–255, got " + b);
            }
            int wordIdx = b / BITS_PER_WORD;
            int bitIdx = b % BITS_PER_WORD;
            words[wordIdx] |= (1L << bitIdx);
        }
        return new ByteSet(words);
    }

    /**
     * Creates a ByteSet containing all bytes from {@code lo} to {@code hi} inclusive.
     *
     * @param lo lower bound (inclusive)
     * @param hi upper bound (inclusive)
     * @return a new ByteSet containing the range
     */
    public static ByteSet range(int lo, int hi) {
        if (lo < 0 || hi > 255 || lo > hi) {
            throw new IllegalArgumentException(
                    "Range must be 0–255 with lo <= hi, got [" + lo + ", " + hi + "]");
        }
        long[] words = new long[WORD_COUNT];
        for (int b = lo; b <= hi; b++) {
            int wordIdx = b / BITS_PER_WORD;
            int bitIdx = b % BITS_PER_WORD;
            words[wordIdx] |= (1L << bitIdx);
        }
        return new ByteSet(words);
    }

    /**
     * Returns the union of this set and another (bitwise OR).
     *
     * @param other the set to union with
     * @return a new ByteSet with bytes from both sets
     */
    public ByteSet union(ByteSet other) {
        long[] result = new long[WORD_COUNT];
        for (int i = 0; i < WORD_COUNT; i++) {
            result[i] = this.words[i] | other.words[i];
        }
        return new ByteSet(result);
    }

    /**
     * Returns the intersection of this set and another (bitwise AND).
     *
     * @param other the set to intersect with
     * @return a new ByteSet with bytes common to both sets
     */
    public ByteSet intersect(ByteSet other) {
        long[] result = new long[WORD_COUNT];
        for (int i = 0; i < WORD_COUNT; i++) {
            result[i] = this.words[i] & other.words[i];
        }
        return new ByteSet(result);
    }

    /**
     * Returns the difference of this set and another (bitwise AND NOT).
     *
     * @param other the set to subtract
     * @return a new ByteSet with bytes in this set but not in other
     */
    public ByteSet minus(ByteSet other) {
        long[] result = new long[WORD_COUNT];
        for (int i = 0; i < WORD_COUNT; i++) {
            result[i] = this.words[i] & ~other.words[i];
        }
        return new ByteSet(result);
    }

    /**
     * Returns the complement of this set (bitwise NOT).
     *
     * @return a new ByteSet with all bytes not in this set
     */
    public ByteSet complement() {
        long[] result = new long[WORD_COUNT];
        for (int i = 0; i < WORD_COUNT; i++) {
            result[i] = ~this.words[i];
        }
        return new ByteSet(result);
    }

    /**
     * Checks if the given byte is in this set.
     *
     * @param b the byte to check (0–255)
     * @return true if b is in this set
     */
    public boolean contains(int b) {
        if (b < 0 || b > 255) {
            return false;
        }
        int wordIdx = b / BITS_PER_WORD;
        int bitIdx = b % BITS_PER_WORD;
        return ((this.words[wordIdx] >>> bitIdx) & 1L) == 1L;
    }

    /**
     * Checks if this set is empty.
     *
     * @return true if no bytes are in this set
     */
    public boolean isEmpty() {
        for (long word : words) {
            if (word != 0L) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of bytes in this set.
     *
     * @return the cardinality
     */
    public int size() {
        int count = 0;
        for (long word : words) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /**
     * Returns a new set with the complementary case of ASCII letters added.
     * For example, if this set contains 'a', the result also contains 'A'.
     * Non-ASCII bytes are left unchanged.
     *
     * @return a new ByteSet with case variants
     */
    public ByteSet foldCase() {
        long[] result = Arrays.copyOf(this.words, WORD_COUNT);
        // For each ASCII letter in [a-z] or [A-Z], add its opposite case
        for (int b = 'a'; b <= 'z'; b++) {
            int wordIdx = b / BITS_PER_WORD;
            int bitIdx = b % BITS_PER_WORD;
            if (((this.words[wordIdx] >>> bitIdx) & 1L) == 1L) {
                // Byte b is in the set; add its uppercase variant
                int upper = b - ('a' - 'A');
                int upperWordIdx = upper / BITS_PER_WORD;
                int upperBitIdx = upper % BITS_PER_WORD;
                result[upperWordIdx] |= (1L << upperBitIdx);
            }
        }
        for (int b = 'A'; b <= 'Z'; b++) {
            int wordIdx = b / BITS_PER_WORD;
            int bitIdx = b % BITS_PER_WORD;
            if (((this.words[wordIdx] >>> bitIdx) & 1L) == 1L) {
                // Byte b is in the set; add its lowercase variant
                int lower = b + ('a' - 'A');
                int lowerWordIdx = lower / BITS_PER_WORD;
                int lowerBitIdx = lower % BITS_PER_WORD;
                result[lowerWordIdx] |= (1L << lowerBitIdx);
            }
        }
        return new ByteSet(result);
    }

    /**
     * Returns a short, human-readable description of this set for debugging.
     * Examples: "a", "[0-9]", "ANY", "[^\"\\]".
     *
     * @return a descriptive string
     */
    public String describe() {
        if (isEmpty()) {
            return "∅";
        }
        if (equals(ALL)) {
            return "ANY";
        }
        if (size() == 1) {
            for (int b = 0; b < 256; b++) {
                if (contains(b)) {
                    return describeByte(b);
                }
            }
        }
        // For small sets, try to describe as ranges or a list
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (int b = 0; b < 256; b++) {
            if (contains(b)) {
                if (count > 0) sb.append(" ");
                sb.append(describeByte(b));
                count++;
                if (count > 5) {
                    sb.append("...");
                    break;
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String describeByte(int b) {
        if (b >= 32 && b <= 126 && b != '\\' && b != '"' && b != '\'') {
            return String.valueOf((char) b);
        }
        return String.format("\\x%02X", b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByteSet other)) return false;
        return Arrays.equals(this.words, other.words);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(words);
    }

    @Override
    public String toString() {
        return describe();
    }
}
