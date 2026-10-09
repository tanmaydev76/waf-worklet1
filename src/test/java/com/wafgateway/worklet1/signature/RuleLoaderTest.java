package com.wafgateway.worklet1.signature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RuleLoader: parsing rules.txt by hand, and rejecting malformed
 * input with a message naming the offending rule.
 */
class RuleLoaderTest {

    @TempDir
    Path tempDir;

    private Path writeFile(String content) throws IOException {
        Path file = tempDir.resolve("rules.txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void testLoadsTheRealRulesFile() throws RuleLoadException {
        List<Rule> rules = RuleLoader.load("rules.txt");
        assertEquals(14, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r.id().equals("SQL-01")));
        assertTrue(rules.stream().anyMatch(r -> r.id().equals("PT-03")));
    }

    @Test
    void testBlankLinesAndCommentsIgnored() throws IOException, RuleLoadException {
        Path file = writeFile(
                "# this is a comment\n"
                        + "\n"
                        + "   \n"
                        + "SQL-01   SQLI   HIGH   union\\s+select\n"
                        + "# another comment\n"
                        + "SQL-02   SQLI   HIGH   drop\\s+table\n");
        List<Rule> rules = RuleLoader.load(file.toString());
        assertEquals(2, rules.size());
    }

    @Test
    void testDuplicateIdRejected() throws IOException {
        Path file = writeFile(
                "SQL-01   SQLI   HIGH   union\\s+select\n"
                        + "SQL-01   SQLI   HIGH   drop\\s+table\n");
        RuleLoadException ex = assertThrows(RuleLoadException.class, () -> RuleLoader.load(file.toString()));
        assertTrue(ex.getMessage().contains("SQL-01"));
        assertTrue(ex.getMessage().toLowerCase().contains("duplicate"));
    }

    @Test
    void testUnknownSeverityRejected() throws IOException {
        Path file = writeFile("SQL-01   SQLI   SUPERHIGH   union\\s+select\n");
        RuleLoadException ex = assertThrows(RuleLoadException.class, () -> RuleLoader.load(file.toString()));
        assertTrue(ex.getMessage().contains("SQL-01"));
        assertTrue(ex.getMessage().contains("SUPERHIGH"));
    }

    @Test
    void testBadPatternReportsRuleId() throws IOException {
        Path file = writeFile("SQL-01   SQLI   HIGH   (unclosed\n");
        RuleLoadException ex = assertThrows(RuleLoadException.class, () -> RuleLoader.load(file.toString()));
        assertTrue(ex.getMessage().contains("SQL-01"));
    }

    @Test
    void testBadPatternAmongManyReportsCorrectId() throws IOException {
        Path file = writeFile(
                "SQL-01   SQLI   HIGH   union\\s+select\n"
                        + "SQL-02   SQLI   HIGH   fine\\s+pattern\n"
                        + "SQL-03   SQLI   HIGH   [bad\n"
                        + "SQL-04   SQLI   HIGH   also\\s+fine\n");
        RuleLoadException ex = assertThrows(RuleLoadException.class, () -> RuleLoader.load(file.toString()));
        assertTrue(ex.getMessage().contains("SQL-03"));
        assertFalse(ex.getMessage().contains("SQL-01"));
    }

    @Test
    void testMissingFieldsRejected() throws IOException {
        Path file = writeFile("SQL-01   SQLI\n"); // missing severity and pattern
        assertThrows(RuleLoadException.class, () -> RuleLoader.load(file.toString()));
    }

    @Test
    void testMissingSourceRejected() {
        assertThrows(RuleLoadException.class, () -> RuleLoader.load("does-not-exist-anywhere.txt"));
    }

    @Test
    void testPatternMayContainWhitespace() throws IOException, RuleLoadException {
        Path file = writeFile("SQL-01   SQLI   HIGH   union\\s+(all\\s+)?select\\W\n");
        List<Rule> rules = RuleLoader.load(file.toString());
        assertEquals(1, rules.size());
        assertEquals("union\\s+(all\\s+)?select\\W", rules.get(0).pattern());
    }

    @Test
    void testNewCategoryRequiresNoCodeChange() throws IOException, RuleLoadException {
        Path file = writeFile(
                "SQL-01   SQLI        HIGH      union\\s+select\n"
                        + "LOG-01   LOG4SHELL   CRITICAL  \\$\\{jndi:\n");
        List<Rule> rules = RuleLoader.load(file.toString());
        assertEquals(2, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r.category().equals("LOG4SHELL")));
    }
}
