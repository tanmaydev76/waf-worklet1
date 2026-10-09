package com.wafgateway.worklet1.regex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A recursive-descent parser for regular expressions.
 *
 * <p><b>Grammar:</b>
 * <pre>
 *   union   := concat ('|' concat)*
 *   concat  := repeat*
 *   repeat  := atom ('*' | '+' | '?' | '{m}' | '{m,}' | '{m,n}')*
 *   atom    := '(' union ')' | '(?:' union ')' | '[' class ']' | '.' | '\' escape | literal
 *   class   := '^'? (range | escape | literal)+
 *   range   := literal '-' literal
 *   escape  := '\d' | '\w' | '\s' | 'n' | 't' | 'r' | 'f' | 'v' | '0' | 'x' HH | metachar
 * </pre>
 *
 * <p><b>Desugaring:</b>
 * <ul>
 *   <li>{@code r+ → r r*}</li>
 *   <li>{@code r? → r | ε}</li>
 *   <li>{@code r{m,n} → rrr... (m times) | (r|ε) (n-m times)}</li>
 *   <li>{@code r{m,} → rrr... (m times) r*}</li>
 * </ul>
 *
 * <p><b>Anchors and backreferences are not supported;</b> {@code ^}, {@code $},
 * and {@code \1}, etc., throw {@link RegexSyntaxException}.
 *
 * @author Worklet 1 Team
 */
public class RegexParser {
    private final String pattern;
    private final boolean ignoreCase;
    private int pos;

    /**
     * Parses a regular expression pattern with case-sensitivity.
     *
     * @param pattern the regex pattern string
     * @return the root RegexNode of the parsed tree
     * @throws RegexSyntaxException if the pattern has syntax errors
     */
    public static RegexNode parse(String pattern) throws RegexSyntaxException {
        return parse(pattern, false);
    }

    /**
     * Parses a regular expression pattern.
     *
     * @param pattern    the regex pattern string
     * @param ignoreCase if true, character classes and literals are case-insensitive
     * @return the root RegexNode of the parsed tree
     * @throws RegexSyntaxException if the pattern has syntax errors
     */
    public static RegexNode parse(String pattern, boolean ignoreCase) throws RegexSyntaxException {
        RegexParser parser = new RegexParser(pattern, ignoreCase);
        RegexNode result = parser.union();
        if (parser.pos < pattern.length()) {
            throw new RegexSyntaxException("Unexpected character after pattern", parser.pos);
        }
        return result;
    }

    private RegexParser(String pattern, boolean ignoreCase) {
        this.pattern = pattern;
        this.ignoreCase = ignoreCase;
        this.pos = 0;
    }

    // ============================================================================
    // Grammar rules (top-down)
    // ============================================================================

    private RegexNode union() throws RegexSyntaxException {
        List<RegexNode> alternatives = new ArrayList<>();
        alternatives.add(concat());
        while (peek() == '|') {
            consume('|');
            alternatives.add(concat());
        }
        if (alternatives.size() == 1) {
            return alternatives.get(0);
        }
        return new RegexNode.Union(alternatives);
    }

    private RegexNode concat() throws RegexSyntaxException {
        List<RegexNode> children = new ArrayList<>();
        while (pos < pattern.length() && peek() != '|' && peek() != ')') {
            children.add(repeat());
        }
        if (children.isEmpty()) {
            return new RegexNode.Epsilon();
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        return new RegexNode.Concat(children);
    }

    private RegexNode repeat() throws RegexSyntaxException {
        RegexNode atom = atom();

        while (pos < pattern.length()) {
            char ch = peek();
            if (ch == '*') {
                consume('*');
                atom = new RegexNode.Star(atom);
            } else if (ch == '+') {
                consume('+');
                // r+ → r r*
                atom = new RegexNode.Concat(List.of(atom, new RegexNode.Star(atom)));
            } else if (ch == '?') {
                consume('?');
                // r? → r | ε
                atom = new RegexNode.Union(List.of(atom, new RegexNode.Epsilon()));
            } else if (ch == '{') {
                int before = pos;
                RegexNode newAtom = applyCounter(atom);
                if (pos == before) {
                    // '{' did not form a valid counter; leave it for the next
                    // atom() call to treat as a literal, instead of looping forever.
                    break;
                }
                atom = newAtom;
            } else {
                break;
            }
        }
        return atom;
    }

    private RegexNode atom() throws RegexSyntaxException {
        if (pos >= pattern.length()) {
            throw new RegexSyntaxException("Unexpected end of pattern", pos);
        }

        char ch = peek();

        if (ch == '(') {
            consume('(');
            // Check for non-capturing group (?:...)
            if (peek() == '?' && peekAhead(1) == ':') {
                consume('?');
                consume(':');
                RegexNode result = union();
                if (!tryConsume(')')) {
                    throw new RegexSyntaxException("Unclosed group", pos);
                }
                return result;
            } else {
                // Capturing group (same as non-capturing for our purposes)
                RegexNode result = union();
                if (!tryConsume(')')) {
                    throw new RegexSyntaxException("Unclosed group", pos);
                }
                return result;
            }
        } else if (ch == '[') {
            return charClass();
        } else if (ch == '.') {
            consume('.');
            ByteSet set = ByteSet.ALL;
            if (ignoreCase) {
                set = set.foldCase();
            }
            return new RegexNode.Symbol(set);
        } else if (ch == '\\') {
            return escape();
        } else if (ch == '^') {
            throw new RegexSyntaxException("Anchors (^) are not supported", pos);
        } else if (ch == '$') {
            throw new RegexSyntaxException("Anchors ($) are not supported", pos);
        } else if (ch == '*' || ch == '+' || ch == '?' || ch == '|' || ch == ')' || ch == ']') {
            throw new RegexSyntaxException("Nothing to repeat or unexpected character '" + ch + "'", pos);
        } else if (ch == '{') {
            // A literal {
            consume('{');
            ByteSet set = ByteSet.of('{');
            if (ignoreCase) {
                set = set.foldCase();
            }
            return new RegexNode.Symbol(set);
        } else {
            // Literal character
            return literal();
        }
    }

    private RegexNode literal() throws RegexSyntaxException {
        char ch = consume();

        if (ch <= 127) {
            // ASCII: a single byte
            ByteSet set = ByteSet.of((int) ch);
            if (ignoreCase) {
                set = set.foldCase();
            }
            return new RegexNode.Symbol(set);
        }

        // Non-ASCII: the character's UTF-8 encoding is a *sequence* of bytes that
        // must match in order, so this becomes a Concat of one Symbol per byte —
        // not a single Symbol whose set is the union of those bytes (which would
        // wrongly match any one of them).
        byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
        List<RegexNode> byteNodes = new ArrayList<>();
        for (byte b : bytes) {
            byteNodes.add(new RegexNode.Symbol(ByteSet.of(b & 0xFF)));
        }
        if (byteNodes.size() == 1) {
            return byteNodes.get(0);
        }
        return new RegexNode.Concat(byteNodes);
    }

    private RegexNode escape() throws RegexSyntaxException {
        consume('\\');
        if (pos >= pattern.length()) {
            throw new RegexSyntaxException("Incomplete escape sequence", pos);
        }

        char ch = consume();
        if (ch == 'd') return new RegexNode.Symbol(ByteSet.DIGIT);
        if (ch == 'D') return new RegexNode.Symbol(ByteSet.DIGIT.complement());
        if (ch == 'w') return new RegexNode.Symbol(ByteSet.WORD);
        if (ch == 'W') return new RegexNode.Symbol(ByteSet.WORD.complement());
        if (ch == 's') return new RegexNode.Symbol(ByteSet.SPACE);
        if (ch == 'S') return new RegexNode.Symbol(ByteSet.SPACE.complement());
        if (ch == 'n') return new RegexNode.Symbol(ByteSet.of('\n'));
        if (ch == 't') return new RegexNode.Symbol(ByteSet.of('\t'));
        if (ch == 'r') return new RegexNode.Symbol(ByteSet.of('\r'));
        if (ch == 'f') return new RegexNode.Symbol(ByteSet.of('\f'));
        if (ch == 'v') return new RegexNode.Symbol(ByteSet.of('\013')); // Vertical tab
        if (ch == '0') return new RegexNode.Symbol(ByteSet.of(0));
        if (ch == 'x') {
            if (pos + 2 > pattern.length()) {
                throw new RegexSyntaxException("Invalid \\x escape: need 2 hex digits", pos - 2);
            }
            String hexStr = pattern.substring(pos, pos + 2);
            pos += 2;
            try {
                int value = Integer.parseInt(hexStr, 16);
                return new RegexNode.Symbol(ByteSet.of(value));
            } catch (NumberFormatException e) {
                throw new RegexSyntaxException("Invalid \\x escape: '" + hexStr + "' is not hex", pos - 2);
            }
        }
        if (ch == '(') return new RegexNode.Symbol(ByteSet.of('('));
        if (ch == ')') return new RegexNode.Symbol(ByteSet.of(')'));
        if (ch == '[') return new RegexNode.Symbol(ByteSet.of('['));
        if (ch == ']') return new RegexNode.Symbol(ByteSet.of(']'));
        if (ch == '{') return new RegexNode.Symbol(ByteSet.of('{'));
        if (ch == '}') return new RegexNode.Symbol(ByteSet.of('}'));
        if (ch == '.') return new RegexNode.Symbol(ByteSet.of('.'));
        if (ch == '*') return new RegexNode.Symbol(ByteSet.of('*'));
        if (ch == '+') return new RegexNode.Symbol(ByteSet.of('+'));
        if (ch == '?') return new RegexNode.Symbol(ByteSet.of('?'));
        if (ch == '|') return new RegexNode.Symbol(ByteSet.of('|'));
        if (ch == '\\') return new RegexNode.Symbol(ByteSet.of('\\'));
        if (ch == '-') return new RegexNode.Symbol(ByteSet.of('-'));
        if (ch == '^') return new RegexNode.Symbol(ByteSet.of('^'));
        if (ch == '$') return new RegexNode.Symbol(ByteSet.of('$'));
        throw new RegexSyntaxException("Invalid escape sequence '\\" + ch + "'", pos - 2);
    }

    private RegexNode charClass() throws RegexSyntaxException {
        consume('[');
        boolean negated = false;
        if (peek() == '^') {
            negated = true;
            consume('^');
        }

        ByteSet set = ByteSet.EMPTY;
        boolean hasContent = false;

        while (pos < pattern.length() && peek() != ']') {
            hasContent = true;
            if (peek() == '\\') {
                set = set.union(parseClassEscape());
            } else if (peekAhead(1) == '-' && peekAhead(2) != ']' && pos + 2 < pattern.length()) {
                // Range: a-z
                char lo = consume();
                consume('-');
                char hi = consume();
                if (lo > hi) {
                    throw new RegexSyntaxException("Invalid character range '" + lo + "-" + hi + "'", pos - 3);
                }
                set = set.union(ByteSet.range(lo, hi));
            } else {
                // Single character
                char ch = consume();
                set = set.union(ByteSet.of(ch));
            }
        }

        if (!hasContent) {
            throw new RegexSyntaxException("Empty character class", pos);
        }

        if (!tryConsume(']')) {
            throw new RegexSyntaxException("Unterminated character class", pos);
        }

        if (negated) {
            set = set.complement();
        }

        if (ignoreCase) {
            set = set.foldCase();
        }

        return new RegexNode.Symbol(set);
    }

    private ByteSet parseClassEscape() throws RegexSyntaxException {
        consume('\\');
        if (pos >= pattern.length()) {
            throw new RegexSyntaxException("Incomplete escape in character class", pos);
        }

        char ch = consume();
        if (ch == 'd') return ByteSet.DIGIT;
        if (ch == 'D') return ByteSet.DIGIT.complement();
        if (ch == 'w') return ByteSet.WORD;
        if (ch == 'W') return ByteSet.WORD.complement();
        if (ch == 's') return ByteSet.SPACE;
        if (ch == 'S') return ByteSet.SPACE.complement();
        if (ch == 'n') return ByteSet.of('\n');
        if (ch == 't') return ByteSet.of('\t');
        if (ch == 'r') return ByteSet.of('\r');
        if (ch == 'f') return ByteSet.of('\f');
        if (ch == 'v') return ByteSet.of('\013'); // Vertical tab
        if (ch == '0') return ByteSet.of(0);
        if (ch == 'x') {
            if (pos + 2 > pattern.length()) {
                throw new RegexSyntaxException("Invalid \\x escape in class: need 2 hex digits", pos - 2);
            }
            String hexStr = pattern.substring(pos, pos + 2);
            pos += 2;
            try {
                int value = Integer.parseInt(hexStr, 16);
                return ByteSet.of(value);
            } catch (NumberFormatException e) {
                throw new RegexSyntaxException("Invalid \\x escape in class: '" + hexStr + "'", pos - 2);
            }
        }
        if (ch == ']' || ch == '-' || ch == '\\' || ch == '^') return ByteSet.of(ch);
        throw new RegexSyntaxException("Invalid escape in character class '\\" + ch + "'", pos - 2);
    }

    private RegexNode applyCounter(RegexNode atom) throws RegexSyntaxException {
        int startPos = pos;
        consume('{');

        // Parse the counter: {m}, {m,}, or {m,n}
        if (!isDigit(peek())) {
            // Not a valid counter; backtrack and treat { as literal
            pos = startPos;
            return atom;
        }

        int m = parseInteger();
        if (m < 0 || m > 255) {
            throw new RegexSyntaxException("Repeat count must be 0–255", startPos);
        }

        int n = m; // Default: {m} means exactly m
        boolean unbounded = false; // {m,} means m or more, with no finite upper bound

        if (peek() == ',') {
            consume(',');
            if (peek() == '}') {
                // {m,}: m or more
                unbounded = true;
            } else if (isDigit(peek())) {
                // {m,n}
                n = parseInteger();
                if (n < m || n > 255) {
                    throw new RegexSyntaxException("Invalid repeat range", startPos);
                }
            } else {
                throw new RegexSyntaxException("Invalid counter syntax", startPos);
            }
        }

        if (!tryConsume('}')) {
            // Not a valid counter
            pos = startPos;
            return atom;
        }

        // Desugar: {m,n} → m copies + (n-m) copies of (atom | ε)
        //          {m,}  → m copies + atom* (true unbounded repetition, not a bounded cap)
        List<RegexNode> parts = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            parts.add(atom);
        }
        if (unbounded) {
            parts.add(new RegexNode.Star(atom));
        } else {
            for (int i = m; i < n; i++) {
                parts.add(new RegexNode.Union(List.of(atom, new RegexNode.Epsilon())));
            }
        }

        if (parts.isEmpty()) {
            return new RegexNode.Epsilon();
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return new RegexNode.Concat(parts);
    }

    // ============================================================================
    // Helper methods
    // ============================================================================

    private char peek() {
        if (pos >= pattern.length()) {
            return '\0';
        }
        return pattern.charAt(pos);
    }

    private char peekAhead(int offset) {
        int idx = pos + offset;
        if (idx >= pattern.length()) {
            return '\0';
        }
        return pattern.charAt(idx);
    }

    private char consume() throws RegexSyntaxException {
        if (pos >= pattern.length()) {
            throw new RegexSyntaxException("Unexpected end of pattern", pos);
        }
        return pattern.charAt(pos++);
    }

    private void consume(char expected) throws RegexSyntaxException {
        if (pos >= pattern.length() || pattern.charAt(pos) != expected) {
            throw new RegexSyntaxException("Expected '" + expected + "'", pos);
        }
        pos++;
    }

    private boolean tryConsume(char ch) {
        if (pos < pattern.length() && pattern.charAt(pos) == ch) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private int parseInteger() throws RegexSyntaxException {
        if (pos >= pattern.length() || !isDigit(peek())) {
            throw new RegexSyntaxException("Expected digit", pos);
        }
        int value = 0;
        while (pos < pattern.length() && isDigit(peek())) {
            value = value * 10 + (consume() - '0');
        }
        return value;
    }
}
