package com.wafgateway.worklet1.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionIdValidator: exactly 32 lowercase hex characters, nothing else.
 */
class SessionIdValidatorTest {

    private static SessionIdValidator newValidator() throws Exception {
        return new SessionIdValidator(10_000);
    }

    @Test
    void testValid32LowercaseHexChars() throws Exception {
        SessionIdValidator validator = newValidator();
        assertTrue(validator.isValid("7f3a2b9c0d1e4f5a6b7c8d9e0f1a2b3c"));
        assertTrue(validator.isValid("00000000000000000000000000000000".substring(0, 32)));
        assertTrue(validator.isValid("ffffffffffffffffffffffffffffffff"));
    }

    @Test
    void test31CharsInvalid() throws Exception {
        SessionIdValidator validator = newValidator();
        assertFalse(validator.isValid("7f3a2b9c0d1e4f5a6b7c8d9e0f1a2b3")); // 31 chars
    }

    @Test
    void test33CharsInvalid() throws Exception {
        SessionIdValidator validator = newValidator();
        assertFalse(validator.isValid("7f3a2b9c0d1e4f5a6b7c8d9e0f1a2b3cd")); // 33 chars
    }

    @Test
    void testUppercaseHexInvalid() throws Exception {
        SessionIdValidator validator = newValidator();
        assertFalse(validator.isValid("7F3A2B9C0D1E4F5A6B7C8D9E0F1A2B3C"));
    }

    @Test
    void testSqlInjectionAttemptInvalid() throws Exception {
        SessionIdValidator validator = newValidator();
        assertFalse(validator.isValid("7f3a' or 1=1--"));
    }

    @Test
    void testPathTraversalAttemptInvalid() throws Exception {
        SessionIdValidator validator = newValidator();
        assertFalse(validator.isValid("../../etc/passwd"));
    }

    @Test
    void testEmptyStringInvalid() throws Exception {
        SessionIdValidator validator = newValidator();
        assertFalse(validator.isValid(""));
    }

    @Test
    void testNullInvalid() throws Exception {
        SessionIdValidator validator = newValidator();
        assertFalse(validator.isValid(null));
    }

    @Test
    void testLongStringInvalidAndFast() throws Exception {
        SessionIdValidator validator = newValidator();
        String longInput = "a".repeat(5000);

        long start = System.currentTimeMillis();
        boolean result = validator.isValid(longInput);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result);
        assertTrue(elapsed < 1000, "validation took " + elapsed + "ms, expected well under 1000ms");
    }

    @Test
    void testStateCountIsReasonable() throws Exception {
        SessionIdValidator validator = newValidator();
        // 32 distinct "need N more hex digits" live states (each a different
        // distance from acceptance, so none can merge) + 1 accepting state
        // + 1 dead state.
        assertEquals(34, validator.stateCount());
    }
}
