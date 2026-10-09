package com.wafgateway.worklet1.regex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RegexParser: recursive-descent regex parser with desugaring.
 */
class RegexParserTest {

    // ========================================================================
    // Literals and basic atoms
    // ========================================================================

    @Test
    void testLiteral() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a");
        assertInstanceOf(RegexNode.Symbol.class, node);
    }

    @Test
    void testLiteralString() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("hello");
        assertInstanceOf(RegexNode.Concat.class, node);
    }

    // ========================================================================
    // Concatenation and union precedence
    // ========================================================================

    @Test
    void testConcatenation() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("ab");
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        assertEquals(2, concat.children().size());
    }

    @Test
    void testUnion() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a|b");
        assertInstanceOf(RegexNode.Union.class, node);
        RegexNode.Union union = (RegexNode.Union) node;
        assertEquals(2, union.children().size());
    }

    @Test
    void testUnionPrecedence() throws RegexSyntaxException {
        // "ab|c*" should parse as Union(Concat(a,b), Star(c))
        RegexNode node = RegexParser.parse("ab|c*");
        assertInstanceOf(RegexNode.Union.class, node);
        RegexNode.Union union = (RegexNode.Union) node;
        assertEquals(2, union.children().size());
        assertInstanceOf(RegexNode.Concat.class, union.children().get(0));
        assertInstanceOf(RegexNode.Star.class, union.children().get(1));
    }

    @Test
    void testMultipleUnions() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a|b|c");
        assertInstanceOf(RegexNode.Union.class, node);
        RegexNode.Union union = (RegexNode.Union) node;
        assertEquals(3, union.children().size());
    }

    // ========================================================================
    // Repetition
    // ========================================================================

    @Test
    void testStar() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a*");
        assertInstanceOf(RegexNode.Star.class, node);
    }

    @Test
    void testPlus() throws RegexSyntaxException {
        // r+ should desugar to r r*
        RegexNode node = RegexParser.parse("a+");
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        assertEquals(2, concat.children().size());
        assertInstanceOf(RegexNode.Star.class, concat.children().get(1));
    }

    @Test
    void testOptional() throws RegexSyntaxException {
        // r? should desugar to r|ε
        RegexNode node = RegexParser.parse("a?");
        assertInstanceOf(RegexNode.Union.class, node);
        RegexNode.Union union = (RegexNode.Union) node;
        assertEquals(2, union.children().size());
        assertInstanceOf(RegexNode.Epsilon.class, union.children().get(1));
    }

    // ========================================================================
    // Character classes
    // ========================================================================

    @Test
    void testCharClass() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("[abc]");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('a'));
        assertTrue(sym.set().contains('b'));
        assertTrue(sym.set().contains('c'));
        assertFalse(sym.set().contains('d'));
    }

    @Test
    void testCharClassRange() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("[a-z]");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('a'));
        assertTrue(sym.set().contains('m'));
        assertTrue(sym.set().contains('z'));
        assertFalse(sym.set().contains('0'));
    }

    @Test
    void testCharClassNegated() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("[^abc]");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertFalse(sym.set().contains('a'));
        assertTrue(sym.set().contains('d'));
    }

    @Test
    void testCharClassWithEscape() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("[\\d-]");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        for (char c = '0'; c <= '9'; c++) {
            assertTrue(sym.set().contains(c));
        }
        assertTrue(sym.set().contains('-'));
    }

    @Test
    void testCharClassEmpty() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("[]"));
    }

    @Test
    void testCharClassUnterminated() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("[abc"));
    }

    // ========================================================================
    // Escape sequences
    // ========================================================================

    @Test
    void testEscapeDigit() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\d");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('0'));
        assertTrue(sym.set().contains('9'));
        assertFalse(sym.set().contains('a'));
    }

    @Test
    void testEscapeNonDigit() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\D");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertFalse(sym.set().contains('0'));
        assertTrue(sym.set().contains('a'));
    }

    @Test
    void testEscapeWord() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\w");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('a'));
        assertTrue(sym.set().contains('Z'));
        assertTrue(sym.set().contains('0'));
        assertTrue(sym.set().contains('_'));
        assertFalse(sym.set().contains('-'));
    }

    @Test
    void testEscapeNonWord() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\W");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertFalse(sym.set().contains('a'));
        assertTrue(sym.set().contains(' '));
    }

    @Test
    void testEscapeSpace() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\s");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains(' '));
        assertTrue(sym.set().contains('\t'));
        assertTrue(sym.set().contains('\n'));
        assertFalse(sym.set().contains('a'));
    }

    @Test
    void testEscapeNewline() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\n");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('\n'));
    }

    @Test
    void testEscapeHex() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\x41");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('A'));
    }

    @Test
    void testEscapeMetacharacter() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("\\.");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('.'));
    }

    // ========================================================================
    // Dot and groups
    // ========================================================================

    @Test
    void testDot() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse(".");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertEquals(256, sym.set().size());
    }

    @Test
    void testCapturingGroup() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("(abc)");
        assertInstanceOf(RegexNode.Concat.class, node);
    }

    @Test
    void testNonCapturingGroup() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("(?:abc)");
        assertInstanceOf(RegexNode.Concat.class, node);
    }

    @Test
    void testGroupUnion() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("(a|b)c");
        assertInstanceOf(RegexNode.Concat.class, node);
    }

    // ========================================================================
    // Counters {m}, {m,}, {m,n}
    // ========================================================================

    @Test
    void testCounterExact() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a{3}");
        // Should create Concat with exactly 3 copies of 'a', no Star/Union
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        assertEquals(3, concat.children().size());
        for (RegexNode child : concat.children()) {
            assertInstanceOf(RegexNode.Symbol.class, child);
        }
    }

    @Test
    void testCounterMin() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a{2,}");
        // Desugar rule is "m copies + r*" — a true unbounded Star, not a
        // fixed-size cap. Verify the last part is an actual Star node.
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        assertEquals(3, concat.children().size()); // 2 literal copies + 1 Star
        assertInstanceOf(RegexNode.Symbol.class, concat.children().get(0));
        assertInstanceOf(RegexNode.Symbol.class, concat.children().get(1));
        assertInstanceOf(RegexNode.Star.class, concat.children().get(2));
    }

    @Test
    void testCounterMinZero() throws RegexSyntaxException {
        // a{0,} is equivalent to a*
        RegexNode node = RegexParser.parse("a{0,}");
        assertInstanceOf(RegexNode.Star.class, node);
    }

    @Test
    void testCounterRange() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a{2,4}");
        // Should create Concat with 2 copies + 2 optional (Union-with-epsilon) copies
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        assertEquals(4, concat.children().size());
        assertInstanceOf(RegexNode.Symbol.class, concat.children().get(0));
        assertInstanceOf(RegexNode.Symbol.class, concat.children().get(1));
        assertInstanceOf(RegexNode.Union.class, concat.children().get(2));
        assertInstanceOf(RegexNode.Union.class, concat.children().get(3));
    }

    @Test
    void testLiteralBrace() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("{");
        assertInstanceOf(RegexNode.Symbol.class, node);
    }

    @Test
    void testLiteralBraceAfterAtomDoesNotHang() throws RegexSyntaxException {
        // Regression test: "a{" with no valid counter following must not
        // infinite-loop in repeat(); '{' should fall back to a literal.
        RegexNode node = RegexParser.parse("a{");
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        assertEquals(2, concat.children().size());
    }

    @Test
    void testBraceWithNonDigitContentDoesNotHang() throws RegexSyntaxException {
        // Regression test: "{" followed by non-counter content must not
        // infinite-loop; the whole thing becomes literal characters.
        RegexNode node = RegexParser.parse("a{abc}");
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        assertEquals(6, concat.children().size()); // a { a b c }
    }

    @Test
    void testCounterInvalid() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("a{5,2}"));
    }

    // ========================================================================
    // Case insensitivity
    // ========================================================================

    @Test
    void testIgnoreCase() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a", true);
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('a'));
        assertTrue(sym.set().contains('A'));
    }

    @Test
    void testIgnoreCaseCharClass() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("[x-z]", true);
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertTrue(sym.set().contains('x'));
        assertTrue(sym.set().contains('X'));
        assertTrue(sym.set().contains('y'));
        assertTrue(sym.set().contains('Y'));
    }

    // ========================================================================
    // Error handling with position
    // ========================================================================

    @Test
    void testErrorUnclosedGroup() throws RegexSyntaxException {
        RegexSyntaxException ex = assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("(ab"));
        assertTrue(ex.getPosition() >= 0);
    }

    @Test
    void testErrorUnexpectedCloseParen() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("ab)"));
    }

    @Test
    void testErrorNothingToRepeat() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("*a"));
    }

    @Test
    void testErrorAnchorCaret() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("^abc"));
    }

    @Test
    void testErrorAnchorDollar() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("abc$"));
    }

    @Test
    void testErrorBadHexEscape() throws RegexSyntaxException {
        assertThrows(RegexSyntaxException.class, () -> RegexParser.parse("\\xZZ"));
    }

    // ========================================================================
    // Complex patterns
    // ========================================================================

    @Test
    void testComplexPattern1() throws RegexSyntaxException {
        // "union\s+select" - used for SQL injection detection
        RegexNode node = RegexParser.parse("union\\s+select");
        assertNotNull(node);
        String tree = node.toTree();
        assertNotNull(tree);
        assertTrue(tree.length() > 0);
    }

    @Test
    void testComplexPattern2() throws RegexSyntaxException {
        // "admin' or 1=1--"
        RegexNode node = RegexParser.parse("admin.*or.*1=1");
        assertNotNull(node);
    }

    @Test
    void testComplexPattern3() throws RegexSyntaxException {
        // Character class with range and negation
        RegexNode node = RegexParser.parse("[^\"\\\\]");
        assertInstanceOf(RegexNode.Symbol.class, node);
        RegexNode.Symbol sym = (RegexNode.Symbol) node;
        assertFalse(sym.set().contains('"'));
        assertFalse(sym.set().contains('\\'));
        assertTrue(sym.set().contains('a'));
    }

    // ========================================================================
    // toTree() printing
    // ========================================================================

    @Test
    void testToTreeSimple() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a");
        String tree = node.toTree();
        assertTrue(tree.contains("Symbol"));
    }

    @Test
    void testToTreeUnion() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a|b|c");
        String tree = node.toTree();
        assertTrue(tree.contains("Union"));
    }

    @Test
    void testToTreeSqlPattern() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("union\\s+select");
        String tree = node.toTree();
        assertNotNull(tree);
        System.out.println("\n=== Tree for 'union\\\\s+select' ===");
        System.out.println(tree);
        System.out.println("===================================\n");
    }

    @Test
    void testToTreeComplex() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("(a|b)*c+");
        String tree = node.toTree();
        assertNotNull(tree);
        assertTrue(tree.length() > 0);
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    void testEmptyString() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("");
        assertInstanceOf(RegexNode.Epsilon.class, node);
    }

    @Test
    void testSingleCharacter() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("x");
        assertInstanceOf(RegexNode.Symbol.class, node);
    }

    @Test
    void testOnlyUnion() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("|");
        assertInstanceOf(RegexNode.Union.class, node);
        RegexNode.Union union = (RegexNode.Union) node;
        assertEquals(2, union.children().size());
    }

    @Test
    void testTrailingUnion() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a|");
        assertInstanceOf(RegexNode.Union.class, node);
    }

    @Test
    void testMultipleStar() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a**");
        assertInstanceOf(RegexNode.Star.class, node);
    }

    @Test
    void testCounterZero() throws RegexSyntaxException {
        RegexNode node = RegexParser.parse("a{0}");
        assertInstanceOf(RegexNode.Epsilon.class, node);
    }

    @Test
    void testNonAsciiLiteralBecomesByteSequence() throws RegexSyntaxException {
        // 'é' (U+00E9) is 2 bytes in UTF-8: 0xC3 0xA9. Matching it must require
        // that exact two-byte sequence, not "any byte in {0xC3, 0xA9}" at one position.
        RegexNode node = RegexParser.parse("é");
        assertInstanceOf(RegexNode.Concat.class, node);
        RegexNode.Concat concat = (RegexNode.Concat) node;
        byte[] expectedBytes = "é".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(expectedBytes.length, concat.children().size());
        for (int i = 0; i < expectedBytes.length; i++) {
            RegexNode.Symbol sym = (RegexNode.Symbol) concat.children().get(i);
            int expected = expectedBytes[i] & 0xFF;
            assertEquals(1, sym.set().size());
            assertTrue(sym.set().contains(expected));
        }
    }

    @Test
    void testDisguisedAttackPatterns() throws RegexSyntaxException {
        // These should all parse without error
        RegexParser.parse("%27%20OR%201%3D1");
        RegexParser.parse("UnIoN/**/SeLeCt");
        RegexParser.parse("&lt;script&gt;");
        RegexParser.parse("..%2F..%2Fetc%2Fpasswd");
        RegexParser.parse("%252e%252e%252f");
    }
}
