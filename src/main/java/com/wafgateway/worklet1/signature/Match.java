package com.wafgateway.worklet1.signature;

/**
 * One attack signature found by {@link SignatureScanner}.
 *
 * @param ruleId    the matching rule's id (e.g. {@code "SQL-01"})
 * @param category  the matching rule's category (e.g. {@code "SQLI"})
 * @param severity  the matching rule's severity
 * @param fieldName which field the match was found in (e.g. {@code "query.id"})
 * @param position  the byte offset of the match within the *normalized*
 *                   field text (not the original, raw field bytes)
 */
public record Match(String ruleId, String category, Severity severity, String fieldName, int position) {
}
