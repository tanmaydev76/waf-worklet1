package com.wafgateway.worklet1.request;

import com.wafgateway.common.HttpRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FieldExtractor: verifies every field name and value produced
 * from a request with a path, query params, headers, a cookie, and a body.
 */
class FieldExtractorTest {

    private static String valueOf(List<Field> fields, String name) {
        for (Field f : fields) {
            if (f.name().equals(name)) {
                return new String(f.value(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static long countOf(List<Field> fields, String name) {
        return fields.stream().filter(f -> f.name().equals(name)).count();
    }

    @Test
    void testFullRequestExtraction() {
        HttpRequest req = new HttpRequest.Builder()
                .method("POST")
                .path("/api/login")
                .queryParam("id", "5")
                .queryParam("tag", "a")
                .queryParam("tag", "b")
                .header("User-Agent", "curl/8.0")
                .header("Content-Type", "application/json")
                .cookie("WAF_SID", "abc123")
                .body("{\"x\":1}")
                .build();

        List<Field> fields = FieldExtractor.extract(req);

        assertEquals("/api/login", valueOf(fields, "path"));

        // query.id: single value + its name field
        assertEquals("5", valueOf(fields, "query.id"));
        assertEquals("id", valueOf(fields, "query.id#name"));
        assertEquals(1, countOf(fields, "query.id"));
        assertEquals(1, countOf(fields, "query.id#name"));

        // query.tag: repeated parameter produces 2 value-fields, 1 name-field
        assertEquals(2, countOf(fields, "query.tag"));
        assertEquals(1, countOf(fields, "query.tag#name"));
        assertEquals("tag", valueOf(fields, "query.tag#name"));
        List<String> tagValues = fields.stream()
                .filter(f -> f.name().equals("query.tag"))
                .map(f -> new String(f.value(), StandardCharsets.UTF_8))
                .toList();
        assertTrue(tagValues.contains("a"));
        assertTrue(tagValues.contains("b"));

        // headers: lowercased field names
        assertEquals("curl/8.0", valueOf(fields, "header.user-agent"));
        assertEquals("application/json", valueOf(fields, "header.content-type"));

        // cookie: name preserved as given
        assertEquals("abc123", valueOf(fields, "cookie.WAF_SID"));

        // body
        assertEquals("{\"x\":1}", valueOf(fields, "body"));
    }

    @Test
    void testEmptyRequestStillProducesPathAndBodyFields() {
        HttpRequest req = new HttpRequest.Builder().build();
        List<Field> fields = FieldExtractor.extract(req);

        assertEquals("/", valueOf(fields, "path"));
        assertEquals("", valueOf(fields, "body"));
        // no query/header/cookie fields at all
        assertTrue(fields.stream().noneMatch(f -> f.name().startsWith("query.")));
        assertTrue(fields.stream().noneMatch(f -> f.name().startsWith("header.")));
        assertTrue(fields.stream().noneMatch(f -> f.name().startsWith("cookie.")));
    }

    @Test
    void testHeaderNameLowercasingHandlesMixedCase() {
        HttpRequest req = new HttpRequest.Builder()
                .header("X-Forwarded-For", "1.2.3.4")
                .build();
        List<Field> fields = FieldExtractor.extract(req);
        assertEquals("1.2.3.4", valueOf(fields, "header.x-forwarded-for"));
        assertNull(valueOf(fields, "header.X-Forwarded-For"));
    }

    @Test
    void testMultipleCookies() {
        HttpRequest req = new HttpRequest.Builder()
                .cookie("a", "1")
                .cookie("b", "2")
                .build();
        List<Field> fields = FieldExtractor.extract(req);
        assertEquals("1", valueOf(fields, "cookie.a"));
        assertEquals("2", valueOf(fields, "cookie.b"));
    }
}
