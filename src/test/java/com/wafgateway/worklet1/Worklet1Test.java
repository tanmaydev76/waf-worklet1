package com.wafgateway.worklet1;

import com.wafgateway.common.HttpRequest;
import com.wafgateway.worklet1.signature.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for Worklet1: the full inspect() pipeline over complete
 * requests, exercising every step and every BlockReason.
 */
class Worklet1Test {

    private static final String VALID_SESSION_ID = "7f3a2b9c0d1e4f5a6b7c8d9e0f1a2b3c";

    private static Worklet1 defaultWorklet1() throws Exception {
        return new Worklet1(Worklet1Config.load());
    }

    private static Worklet1 withConfig(Worklet1Config config) throws Exception {
        return new Worklet1(config);
    }

    @Test
    void testCleanRequestPasses() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/api/cart/add")
                .queryParam("ref", "home")
                .header("Content-Type", "application/json")
                .cookie("WAF_SID", VALID_SESSION_ID)
                .body("{\"item\":\"book\",\"qty\":2}")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertFalse(result.blocked());
        assertNull(result.reason());
        assertTrue(result.matches().isEmpty());
        assertEquals(9, result.tokens().size());
        assertEquals(VALID_SESSION_ID, result.sessionId());
    }

    @Test
    void testSqlInjectionInBodyBlocks() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/api/cart/add")
                .queryParam("ref", "home")
                .header("Content-Type", "application/json")
                .cookie("WAF_SID", VALID_SESSION_ID)
                .body("{\"item\":\"book%27%20OR%201%3D1--\",\"qty\":2}")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertTrue(result.blocked());
        assertEquals(BlockReason.SIGNATURE, result.reason());
        assertTrue(result.matches().stream().anyMatch(m -> m.ruleId().equals("SQL-02") && m.fieldName().equals("body")));
        assertTrue(result.tokens().isEmpty());
    }

    @Test
    void testXssInHeaderAndPathTraversalInPathReportCorrectFields() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder()
                .method("GET").path("/../../etc/passwd")
                .header("Referer", "javascript:alert(1)")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertTrue(result.blocked());
        assertEquals(BlockReason.SIGNATURE, result.reason());
        assertTrue(result.matches().stream().anyMatch(m -> m.ruleId().equals("PT-01") && m.fieldName().equals("path")));
        assertTrue(result.matches().stream().anyMatch(m -> m.ruleId().equals("XSS-02") && m.fieldName().equals("header.referer")));
    }

    @Test
    void testMalformedSessionCookieBlocks() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder()
                .method("GET").path("/")
                .cookie("WAF_SID", "not-a-valid-session-id")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertTrue(result.blocked());
        assertEquals(BlockReason.BAD_SESSION_ID, result.reason());
        assertNull(result.sessionId());
    }

    @Test
    void testMissingSessionCookiePasses() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder().method("GET").path("/").build();

        Worklet1Result result = worklet1.inspect(request);

        assertFalse(result.blocked());
        assertNull(result.sessionId());
    }

    @Test
    void testBodyOverMaxBytesBlocksBeforeScanning() throws Exception {
        Worklet1Config config = new Worklet1Config(
                "rules.txt", 10, 10_000, "WAF_SID", 10_000, Severity.MEDIUM, Worklet1Config.NonJsonPolicy.ALLOW);
        Worklet1 worklet1 = withConfig(config);
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/")
                .body("this body is way more than 10 bytes long")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertTrue(result.blocked());
        assertEquals(BlockReason.BODY_TOO_LARGE, result.reason());
    }

    @Test
    void testInvalidJsonCharacterBlocks() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/")
                .header("Content-Type", "application/json")
                .body("{\"a\": @}")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertTrue(result.blocked());
        assertEquals(BlockReason.INVALID_TOKEN, result.reason());
    }

    @Test
    void testTooManyTokensBlocks() throws Exception {
        Worklet1Config config = new Worklet1Config(
                "rules.txt", 1_048_576, 5, "WAF_SID", 10_000, Severity.MEDIUM, Worklet1Config.NonJsonPolicy.ALLOW);
        Worklet1 worklet1 = withConfig(config);
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/")
                .header("Content-Type", "application/json")
                .body("{\"a\":1,\"b\":2}")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertTrue(result.blocked());
        assertEquals(BlockReason.TOO_MANY_TOKENS, result.reason());
    }

    @Test
    void testNonJsonBodyAllowPolicyPasses() throws Exception {
        Worklet1 worklet1 = defaultWorklet1(); // default config has body.nonJson = ALLOW
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/")
                .header("Content-Type", "text/plain")
                .body("just some text")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertFalse(result.blocked());
        assertTrue(result.tokens().isEmpty());
    }

    @Test
    void testNonJsonBodyBlockPolicyBlocks() throws Exception {
        Worklet1Config config = new Worklet1Config(
                "rules.txt", 1_048_576, 10_000, "WAF_SID", 10_000, Severity.MEDIUM, Worklet1Config.NonJsonPolicy.BLOCK);
        Worklet1 worklet1 = withConfig(config);
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/")
                .header("Content-Type", "text/plain")
                .body("just some text")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertTrue(result.blocked());
        assertEquals(BlockReason.UNSUPPORTED_CONTENT_TYPE, result.reason());
    }

    @Test
    void testHighSeverityThresholdAllowsMediumOnlyRule() throws Exception {
        Worklet1Config config = new Worklet1Config(
                "rules.txt", 1_048_576, 10_000, "WAF_SID", 10_000, Severity.HIGH, Worklet1Config.NonJsonPolicy.ALLOW);
        Worklet1 worklet1 = withConfig(config);
        // SQL-03 (MEDIUM) matches "admin'--"; SQL-01/02/04/06 (HIGH) do not.
        HttpRequest request = new HttpRequest.Builder()
                .method("GET").path("/search")
                .queryParam("q", "admin'--")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertFalse(result.blocked());
    }

    @Test
    void testGetWithNoBodyPasses() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder().method("GET").path("/home").build();

        Worklet1Result result = worklet1.inspect(request);

        assertFalse(result.blocked());
        assertTrue(result.tokens().isEmpty());
    }

    @Test
    void testContentTypeIgnoresCaseAndCharset() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder()
                .method("POST").path("/")
                .header("Content-Type", "APPLICATION/JSON; charset=UTF-8")
                .body("{}")
                .build();

        Worklet1Result result = worklet1.inspect(request);

        assertFalse(result.blocked());
        assertEquals(2, result.tokens().size()); // LBRACE, RBRACE
    }

    @Test
    void testResultListsAreNeverNull() throws Exception {
        Worklet1 worklet1 = defaultWorklet1();
        HttpRequest request = new HttpRequest.Builder().method("GET").path("/").build();

        Worklet1Result result = worklet1.inspect(request);

        assertNotNull(result.matches());
        assertNotNull(result.tokens());
    }
}
