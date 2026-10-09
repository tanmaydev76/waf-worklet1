package com.wafgateway.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An HTTP request with all parsed and normalized metadata for inspection.
 *
 * <p>This is the main input to Worklet 1. The request includes the HTTP method, path,
 * query parameters, headers, cookies, and raw body. Query and headers are already
 * URL-decoded but not lowercased; the normalizer applies lowercasing during scanning.
 *
 * @param method  HTTP method (GET, POST, etc.)
 * @param path    request path (still URL-encoded)
 * @param query   query parameters; key → list of values (both key and value may be URL-encoded)
 * @param headers HTTP headers; header name keys are case-insensitive (by convention lowercase)
 * @param cookies cookies; name → value
 * @param body    raw request body as bytes (may be empty for GET requests)
 */
public record HttpRequest(
        String method,
        String path,
        Map<String, List<String>> query,
        Map<String, String> headers,
        Map<String, String> cookies,
        byte[] body) {

    /**
     * Creates an HttpRequest with validation and defensive copies.
     *
     * @param method  must not be null or empty
     * @param path    must not be null
     * @param query   must not be null; will be wrapped as unmodifiable
     * @param headers must not be null; will be wrapped as unmodifiable
     * @param cookies must not be null; will be wrapped as unmodifiable
     * @param body    must not be null; a defensive copy is made
     */
    public HttpRequest {
        if (method == null || method.isEmpty()) {
            throw new IllegalArgumentException("method must not be null or empty");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (headers == null) {
            throw new IllegalArgumentException("headers must not be null");
        }
        if (cookies == null) {
            throw new IllegalArgumentException("cookies must not be null");
        }
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }

        // Defensive copies and immutability
        query = Collections.unmodifiableMap(new HashMap<>(query));
        headers = Collections.unmodifiableMap(new HashMap<>(headers));
        cookies = Collections.unmodifiableMap(new HashMap<>(cookies));
        body = body.clone();
    }

    /**
     * Fluent builder for constructing HttpRequest instances in tests.
     */
    public static class Builder {
        private String method = "GET";
        private String path = "/";
        private Map<String, List<String>> query = new HashMap<>();
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> cookies = new HashMap<>();
        private byte[] body = new byte[0];

        /**
         * Sets the HTTP method.
         *
         * @param method HTTP method (default "GET")
         * @return this builder
         */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /**
         * Sets the request path.
         *
         * @param path request path (default "/")
         * @return this builder
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Adds a single query parameter.
         *
         * @param name  parameter name
         * @param value parameter value
         * @return this builder
         */
        public Builder queryParam(String name, String value) {
            this.query.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(value);
            return this;
        }

        /**
         * Sets all query parameters.
         *
         * @param query parameter map
         * @return this builder
         */
        public Builder query(Map<String, List<String>> query) {
            this.query = new HashMap<>(query);
            return this;
        }

        /**
         * Adds a single header.
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /**
         * Sets all headers.
         *
         * @param headers header map
         * @return this builder
         */
        public Builder headers(Map<String, String> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }

        /**
         * Adds a single cookie.
         *
         * @param name  cookie name
         * @param value cookie value
         * @return this builder
         */
        public Builder cookie(String name, String value) {
            this.cookies.put(name, value);
            return this;
        }

        /**
         * Sets all cookies.
         *
         * @param cookies cookie map
         * @return this builder
         */
        public Builder cookies(Map<String, String> cookies) {
            this.cookies = new HashMap<>(cookies);
            return this;
        }

        /**
         * Sets the request body.
         *
         * @param body raw body bytes (default empty)
         * @return this builder
         */
        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        /**
         * Sets the request body from a string (UTF-8 encoded).
         *
         * @param body body text
         * @return this builder
         */
        public Builder body(String body) {
            this.body = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return this;
        }

        /**
         * Builds the HttpRequest.
         *
         * @return a new HttpRequest instance
         */
        public HttpRequest build() {
            return new HttpRequest(method, path, query, headers, cookies, body);
        }
    }
}
