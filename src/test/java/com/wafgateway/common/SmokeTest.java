package com.wafgateway.common;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test to verify the Phase 0 setup: Maven, JUnit 5, and shared contracts.
 */
class SmokeTest {

    @Test
    void testTokenTypeEnum() {
        assertNotNull(TokenType.LBRACE);
        assertNotNull(TokenType.STRING);
        assertNotNull(TokenType.NUMBER);
        assertEquals(11, TokenType.values().length);
    }

    @Test
    void testTokenCreation() {
        Token token = new Token(TokenType.STRING, "\"hello\"", 0);
        assertEquals(TokenType.STRING, token.type());
        assertEquals("\"hello\"", token.value());
        assertEquals(0, token.position());
    }

    @Test
    void testTokenValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Token(null, "x", 0));
        assertThrows(IllegalArgumentException.class, () -> new Token(TokenType.STRING, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new Token(TokenType.STRING, "x", -1));
    }

    @Test
    void testHttpRequestBuilderDefault() {
        HttpRequest req = new HttpRequest.Builder().build();
        assertEquals("GET", req.method());
        assertEquals("/", req.path());
        assertTrue(req.query().isEmpty());
        assertTrue(req.headers().isEmpty());
        assertTrue(req.cookies().isEmpty());
        assertEquals(0, req.body().length);
    }

    @Test
    void testHttpRequestBuilderFull() {
        HttpRequest req = new HttpRequest.Builder()
                .method("POST")
                .path("/api/login")
                .queryParam("redirect", "home")
                .header("content-type", "application/json")
                .cookie("session", "abc123")
                .body("{\"user\":\"admin\"}")
                .build();

        assertEquals("POST", req.method());
        assertEquals("/api/login", req.path());
        assertTrue(req.query().containsKey("redirect"));
        assertEquals("application/json", req.headers().get("content-type"));
        assertEquals("abc123", req.cookies().get("session"));
        assertTrue(req.body().length > 0);
    }

    @Test
    void testHttpRequestValidation() {
        assertThrows(IllegalArgumentException.class, () -> new HttpRequest.Builder()
                .method("")
                .build());
        assertThrows(IllegalArgumentException.class, () -> new HttpRequest.Builder()
                .method(null)
                .build());
    }

    @Test
    void testHttpRequestDefensiveCopy() {
        Map<String, String> cookieMap = new HashMap<>();
        cookieMap.put("session", "abc");
        HttpRequest req = new HttpRequest.Builder().cookies(cookieMap).build();

        // Original map is modified, but request cookies are unchanged
        cookieMap.put("another", "xyz");
        assertFalse(req.cookies().containsKey("another"));
    }
}
