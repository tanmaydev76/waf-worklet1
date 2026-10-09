package com.wafgateway.worklet1.signature;

import com.wafgateway.worklet1.request.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized test over the full must-block / must-allow table from
 * CLAUDE.md section 9, plus the listed disguised-attack cases, run through
 * the real {@link RuleLoader} + {@link SignatureScanner} pipeline (so
 * normalization is exercised exactly as it would be in production).
 */
class RulesTest {

    private static SignatureScanner scanner;

    @BeforeAll
    static void setUp() throws Exception {
        List<Rule> rules = RuleLoader.load("rules.txt");
        scanner = new SignatureScanner(rules, 10_000);
    }

    private static List<Match> scan(String text) {
        Field field = new Field("test", text.getBytes(StandardCharsets.UTF_8));
        return scanner.scan(List.of(field));
    }

    static Stream<Arguments> mustBlockCases() {
        return Stream.of(
                Arguments.of("SQL-01", "1 union select password from users"),
                Arguments.of("SQL-02", "admin' or 1=1--"),
                Arguments.of("SQL-03", "admin'--"),
                Arguments.of("SQL-04", "1; drop table users"),
                Arguments.of("SQL-05", "1 and sleep(5)"),
                Arguments.of("SQL-06", "select * from information_schema.tables"),
                Arguments.of("XSS-01", "<script>alert(1)</script>"),
                Arguments.of("XSS-02", "<a href=\"javascript:alert(1)\">"),
                Arguments.of("XSS-03", "<img src=x onerror=alert(1)>"),
                Arguments.of("XSS-04", "<iframe src=evil.com>"),
                Arguments.of("XSS-05", "fetch(document.cookie)"),
                Arguments.of("PT-01", "../../etc/passwd"),
                Arguments.of("PT-02", "file=/etc/passwd"),
                Arguments.of("PT-03", "c:\\windows\\win.ini")
        );
    }

    static Stream<Arguments> mustAllowCases() {
        return Stream.of(
                Arguments.of("SQL-01", "trade union selection committee"),
                Arguments.of("SQL-02", "O'Brien and 2 friends"),
                Arguments.of("SQL-03", "it's -- great"),
                Arguments.of("SQL-04", "see you; drop by later"),
                Arguments.of("SQL-05", "sleep well tonight"),
                Arguments.of("SQL-06", "information about the schema"),
                Arguments.of("XSS-01", "I am learning JavaScript"),
                Arguments.of("XSS-02", "javascript tutorial"),
                Arguments.of("XSS-03", "I am online now"),
                Arguments.of("XSS-04", "the iframe tag is old"),
                Arguments.of("XSS-05", "upload document.pdf"),
                Arguments.of("PT-01", "wait... what?"),
                Arguments.of("PT-02", "/etc/notes.txt"),
                Arguments.of("PT-03", "winning.txt")
        );
    }

    @ParameterizedTest(name = "{0} must block \"{1}\"")
    @MethodSource("mustBlockCases")
    void testMustBlock(String ruleId, String input) {
        List<Match> matches = scan(input);
        assertTrue(matches.stream().anyMatch(m -> m.ruleId().equals(ruleId)),
                "Expected rule " + ruleId + " to match \"" + input + "\" but matches were " + matches);
    }

    @ParameterizedTest(name = "{0} must allow \"{1}\"")
    @MethodSource("mustAllowCases")
    void testMustAllow(String ruleId, String input) {
        List<Match> matches = scan(input);
        assertTrue(matches.isEmpty(),
                "Expected \"" + input + "\" to produce no matches but got " + matches);
    }

    // ========================================================================
    // Disguised attacks from section 9
    // ========================================================================

    static Stream<Arguments> disguisedAttackCases() {
        return Stream.of(
                Arguments.of("%27%20OR%201%3D1", "SQL-02"),
                Arguments.of("UnIoN/**/SeLeCt * from", "SQL-01"),
                Arguments.of("&lt;script&gt;", "XSS-01"),
                Arguments.of("..%2F..%2Fetc%2Fpasswd", "PT-01"),
                Arguments.of("%252e%252e%252f", "PT-01")
        );
    }

    @ParameterizedTest(name = "disguised \"{0}\" caught as {1}")
    @MethodSource("disguisedAttackCases")
    void testDisguisedAttacksCaught(String input, String expectedRuleId) {
        List<Match> matches = scan(input);
        assertTrue(matches.stream().anyMatch(m -> m.ruleId().equals(expectedRuleId)),
                "Expected disguised attack \"" + input + "\" to match " + expectedRuleId
                        + " but matches were " + matches);
    }
}
