package com.wafgateway.worklet1.signature;

/**
 * One attack-signature rule: an id, the category it belongs to, how severe a
 * match is, and the regex pattern that detects it.
 *
 * <p>{@code category} is a plain string rather than an enum deliberately —
 * new categories (e.g. a future {@code LOG4SHELL} category) can be added
 * just by editing {@code rules.txt}, with no code change anywhere in the
 * pipeline (see {@link SignatureScanner}, which groups rules by whatever
 * distinct category strings it finds).
 *
 * @param id       a unique identifier (e.g. {@code "SQL-01"})
 * @param category the rule's category (e.g. {@code "SQLI"}, {@code "XSS"})
 * @param severity how dangerous a match is
 * @param pattern  the regex pattern text that detects this attack
 */
public record Rule(String id, String category, Severity severity, String pattern) {
    /**
     * Validates that every field is present.
     */
    public Rule {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id must not be null or empty");
        if (category == null || category.isEmpty()) throw new IllegalArgumentException("category must not be null or empty");
        if (severity == null) throw new IllegalArgumentException("severity must not be null");
        if (pattern == null || pattern.isEmpty()) throw new IllegalArgumentException("pattern must not be null or empty");
    }
}
