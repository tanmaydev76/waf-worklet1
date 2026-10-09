package com.wafgateway.worklet1.regex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ByteSet: bit-vector implementation of byte sets.
 */
class ByteSetTest {

    @Test
    void testOfSingleByte() {
        ByteSet set = ByteSet.of('a');
        assertTrue(set.contains('a'));
        assertFalse(set.contains('b'));
        assertEquals(1, set.size());
    }

    @Test
    void testOfMultipleBytes() {
        ByteSet set = ByteSet.of('a', 'c', 'e');
        assertTrue(set.contains('a'));
        assertTrue(set.contains('c'));
        assertTrue(set.contains('e'));
        assertFalse(set.contains('b'));
        assertEquals(3, set.size());
    }

    @Test
    void testRange() {
        ByteSet set = ByteSet.range('0', '9');
        for (int i = '0'; i <= '9'; i++) {
            assertTrue(set.contains(i));
        }
        assertFalse(set.contains('a'));
        assertEquals(10, set.size());
    }

    @Test
    void testRangeEdgeCases() {
        ByteSet singleChar = ByteSet.range('x', 'x');
        assertTrue(singleChar.contains('x'));
        assertEquals(1, singleChar.size());
    }

    @Test
    void testEmpty() {
        assertTrue(ByteSet.EMPTY.isEmpty());
        assertEquals(0, ByteSet.EMPTY.size());
        assertFalse(ByteSet.EMPTY.contains('a'));
    }

    @Test
    void testAll() {
        assertEquals(256, ByteSet.ALL.size());
        for (int b = 0; b < 256; b++) {
            assertTrue(ByteSet.ALL.contains(b));
        }
    }

    @Test
    void testUnion() {
        ByteSet a = ByteSet.of('a', 'b');
        ByteSet c = ByteSet.of('c', 'd');
        ByteSet union = a.union(c);
        assertTrue(union.contains('a'));
        assertTrue(union.contains('b'));
        assertTrue(union.contains('c'));
        assertTrue(union.contains('d'));
        assertFalse(union.contains('e'));
        assertEquals(4, union.size());
    }

    @Test
    void testIntersect() {
        ByteSet a = ByteSet.range('a', 'c');
        ByteSet b = ByteSet.range('b', 'd');
        ByteSet inter = a.intersect(b);
        assertFalse(inter.contains('a'));
        assertTrue(inter.contains('b'));
        assertTrue(inter.contains('c'));
        assertFalse(inter.contains('d'));
        assertEquals(2, inter.size());
    }

    @Test
    void testMinus() {
        ByteSet a = ByteSet.range('a', 'e');
        ByteSet b = ByteSet.range('b', 'd');
        ByteSet diff = a.minus(b);
        assertTrue(diff.contains('a'));
        assertFalse(diff.contains('b'));
        assertFalse(diff.contains('c'));
        assertFalse(diff.contains('d'));
        assertTrue(diff.contains('e'));
        assertEquals(2, diff.size());
    }

    @Test
    void testComplement() {
        ByteSet digit = ByteSet.range('0', '9');
        ByteSet nonDigit = digit.complement();
        assertFalse(nonDigit.contains('0'));
        assertFalse(nonDigit.contains('9'));
        assertTrue(nonDigit.contains('a'));
        assertEquals(256 - 10, nonDigit.size());
    }

    @Test
    void testFoldCaseLowerToUpper() {
        ByteSet lowers = ByteSet.range('a', 'z');
        ByteSet folded = lowers.foldCase();
        for (char c = 'a'; c <= 'z'; c++) {
            assertTrue(folded.contains(c), "Missing lowercase " + c);
            assertTrue(folded.contains(Character.toUpperCase(c)), "Missing uppercase " + Character.toUpperCase(c));
        }
        assertEquals(52, folded.size()); // 26 lower + 26 upper
    }

    @Test
    void testFoldCaseUpperToLower() {
        ByteSet uppers = ByteSet.range('A', 'Z');
        ByteSet folded = uppers.foldCase();
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue(folded.contains(c), "Missing uppercase " + c);
            assertTrue(folded.contains(Character.toLowerCase(c)), "Missing lowercase " + Character.toLowerCase(c));
        }
        assertEquals(52, folded.size());
    }

    @Test
    void testFoldCaseMixed() {
        ByteSet set = ByteSet.of('a', 'B', '1');
        ByteSet folded = set.foldCase();
        assertTrue(folded.contains('a'));
        assertTrue(folded.contains('A'));
        assertTrue(folded.contains('b'));
        assertTrue(folded.contains('B'));
        assertTrue(folded.contains('1'));
        assertEquals(5, folded.size());
    }

    @Test
    void testFoldCaseNonASCII() {
        ByteSet set = ByteSet.of(255);
        ByteSet folded = set.foldCase();
        // Non-ASCII bytes should remain unchanged
        assertTrue(folded.contains(255));
        assertEquals(1, folded.size());
    }

    @Test
    void testConstantDigit() {
        for (int i = '0'; i <= '9'; i++) {
            assertTrue(ByteSet.DIGIT.contains(i));
        }
        assertFalse(ByteSet.DIGIT.contains('a'));
    }

    @Test
    void testConstantWord() {
        ByteSet word = ByteSet.WORD;
        // Should contain a-z, A-Z, 0-9, _
        for (char c = 'a'; c <= 'z'; c++) {
            assertTrue(word.contains(c));
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue(word.contains(c));
        }
        for (char c = '0'; c <= '9'; c++) {
            assertTrue(word.contains(c));
        }
        assertTrue(word.contains('_'));
        assertFalse(word.contains('-'));
    }

    @Test
    void testConstantSpace() {
        ByteSet space = ByteSet.SPACE;
        assertTrue(space.contains(' '));
        assertTrue(space.contains('\t'));
        assertTrue(space.contains('\n'));
        assertTrue(space.contains('\r'));
        assertTrue(space.contains('\f'));
        assertTrue(space.contains(11)); // Vertical tab
        assertFalse(space.contains('a'));
    }

    @Test
    void testDescribeEmpty() {
        String desc = ByteSet.EMPTY.describe();
        assertNotNull(desc);
        assertTrue(desc.contains("∅") || desc.isEmpty());
    }

    @Test
    void testDescribeSingle() {
        String desc = ByteSet.of('a').describe();
        assertEquals("a", desc);
    }

    @Test
    void testDescribeAll() {
        String desc = ByteSet.ALL.describe();
        assertEquals("ANY", desc);
    }

    @Test
    void testEqualsAndHashCode() {
        ByteSet a1 = ByteSet.of('a', 'b', 'c');
        ByteSet a2 = ByteSet.of('a', 'b', 'c');
        ByteSet b = ByteSet.of('a', 'b', 'd');

        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
    }

    @Test
    void testContainsOutOfRange() {
        ByteSet set = ByteSet.of('a');
        assertFalse(set.contains(-1));
        assertFalse(set.contains(256));
    }

    @Test
    void testOfInvalidByte() {
        assertThrows(IllegalArgumentException.class, () -> ByteSet.of(-1));
        assertThrows(IllegalArgumentException.class, () -> ByteSet.of(256));
    }

    @Test
    void testRangeInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> ByteSet.range(10, 5));
        assertThrows(IllegalArgumentException.class, () -> ByteSet.range(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> ByteSet.range(0, 256));
    }
}
