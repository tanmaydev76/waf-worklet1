package com.wafgateway.worklet1.request;

/**
 * One piece of request data to be scanned for attack signatures or validated.
 *
 * <p>A single {@link com.wafgateway.common.HttpRequest} is broken into many
 * fields by {@link FieldExtractor} — the path, each query parameter value
 * (and separately, each query parameter *name*), each header, each cookie,
 * and the body — so that {@code SignatureScanner} can check each piece
 * independently and report exactly which field an attack was found in.
 *
 * @param name  the field's identifier (e.g. {@code "path"}, {@code "query.id"},
 *              {@code "header.user-agent"}, {@code "cookie.session"}, {@code "body"})
 * @param value the raw, not-yet-normalized bytes of this field
 */
public record Field(String name, byte[] value) {
    /**
     * Validates that name and value are present.
     */
    public Field {
        if (name == null) throw new IllegalArgumentException("name must not be null");
        if (value == null) throw new IllegalArgumentException("value must not be null");
    }
}
