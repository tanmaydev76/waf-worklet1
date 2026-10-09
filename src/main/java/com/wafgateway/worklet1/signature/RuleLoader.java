package com.wafgateway.worklet1.signature;

import com.wafgateway.worklet1.regex.RegexParser;
import com.wafgateway.worklet1.regex.RegexSyntaxException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads {@link Rule}s from a {@code rules.txt} file.
 *
 * <p><b>Format:</b> one rule per line. Blank lines and lines starting with
 * {@code #} are ignored. The first three whitespace-separated tokens are the
 * id, category, and severity; everything after that (trimmed) is the
 * pattern — so a pattern itself may freely contain whitespace, e.g.
 * {@code union\s+select}.
 *
 * <p>Parsed entirely by hand, scanning character by character for
 * whitespace boundaries — {@code String.split} is off-limits project-wide
 * since it is implemented with {@code java.util.regex} internally.
 *
 * <p>Each pattern is also syntax-checked immediately (by attempting to parse
 * it) so that a typo in {@code rules.txt} fails loudly at startup, naming
 * the exact rule id, rather than surfacing later as a confusing error deep
 * inside DFA construction.
 *
 * @author Worklet 1 Team
 */
public final class RuleLoader {

    private RuleLoader() {
        // Utility class; not instantiable.
    }

    /**
     * Loads rules from a classpath resource, falling back to treating the
     * same string as a filesystem path if no such resource exists.
     *
     * @param source a classpath resource name or a file path
     * @return the loaded rules, in file order
     * @throws RuleLoadException if the source can't be found/read, or any
     *                            line is malformed, duplicated, or has a bad pattern
     */
    public static List<Rule> load(String source) throws RuleLoadException {
        try (InputStream in = openSource(source)) {
            return parse(in, source);
        } catch (IOException e) {
            throw new RuleLoadException("Failed to read rules from '" + source + "': " + e.getMessage(), e);
        }
    }

    private static InputStream openSource(String source) throws RuleLoadException {
        InputStream classpathStream = RuleLoader.class.getClassLoader().getResourceAsStream(source);
        if (classpathStream != null) {
            return classpathStream;
        }
        try {
            return new FileInputStream(source);
        } catch (FileNotFoundException e) {
            throw new RuleLoadException("Rules source not found on classpath or filesystem: '" + source + "'");
        }
    }

    private static List<Rule> parse(InputStream in, String source) throws RuleLoadException, IOException {
        List<Rule> rules = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                Rule rule = parseLine(line, lineNumber, source);

                if (!seenIds.add(rule.id())) {
                    throw new RuleLoadException(
                            "Duplicate rule id '" + rule.id() + "' at " + source + ":" + lineNumber);
                }

                try {
                    RegexParser.parse(rule.pattern());
                } catch (RegexSyntaxException e) {
                    throw new RuleLoadException(
                            "Rule '" + rule.id() + "' at " + source + ":" + lineNumber
                                    + " has an invalid pattern: " + e.getMessage(), e);
                }

                rules.add(rule);
            }
        }

        return rules;
    }

    /**
     * Parses one rule line by hand: id, category, severity (each a
     * whitespace-delimited token), then the pattern (the rest of the line,
     * trimmed).
     */
    private static Rule parseLine(String line, int lineNumber, String source) throws RuleLoadException {
        int i = 0;
        int len = line.length();

        i = skipWhitespace(line, i, len);
        int idStart = i;
        i = skipNonWhitespace(line, i, len);
        if (i == idStart) {
            throw new RuleLoadException("Malformed rule line at " + source + ":" + lineNumber + " (missing id)");
        }
        String id = line.substring(idStart, i);

        i = skipWhitespace(line, i, len);
        int categoryStart = i;
        i = skipNonWhitespace(line, i, len);
        if (i == categoryStart) {
            throw new RuleLoadException(
                    "Malformed rule '" + id + "' at " + source + ":" + lineNumber + " (missing category)");
        }
        String category = line.substring(categoryStart, i);

        i = skipWhitespace(line, i, len);
        int severityStart = i;
        i = skipNonWhitespace(line, i, len);
        if (i == severityStart) {
            throw new RuleLoadException(
                    "Malformed rule '" + id + "' at " + source + ":" + lineNumber + " (missing severity)");
        }
        String severityText = line.substring(severityStart, i);
        Severity severity = parseSeverity(severityText, id, lineNumber, source);

        i = skipWhitespace(line, i, len);
        String pattern = (i < len) ? line.substring(i).trim() : "";
        if (pattern.isEmpty()) {
            throw new RuleLoadException(
                    "Malformed rule '" + id + "' at " + source + ":" + lineNumber + " (missing pattern)");
        }

        return new Rule(id, category, severity, pattern);
    }

    private static Severity parseSeverity(String text, String ruleId, int lineNumber, String source)
            throws RuleLoadException {
        try {
            return Severity.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new RuleLoadException(
                    "Rule '" + ruleId + "' at " + source + ":" + lineNumber + " has unknown severity '" + text + "'");
        }
    }

    private static int skipWhitespace(String line, int i, int len) {
        while (i < len && isSpaceOrTab(line.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int skipNonWhitespace(String line, int i, int len) {
        while (i < len && !isSpaceOrTab(line.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }
}
