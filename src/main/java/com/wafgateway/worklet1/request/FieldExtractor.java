package com.wafgateway.worklet1.request;

import com.wafgateway.common.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Breaks an {@link HttpRequest} into a flat list of {@link Field}s for
 * signature scanning.
 *
 * <p>Every distinct piece of attacker-controlled text in a request becomes
 * its own field, named so the eventual {@code Match} can say exactly where
 * an attack signature was found:
 * <ul>
 *   <li>{@code path} — the request path</li>
 *   <li>{@code query.<name>} — one field per query parameter *value* (a
 *       repeated parameter produces several fields with the same name)</li>
 *   <li>{@code query.<name>#name} — the parameter *name* itself, scanned as
 *       its own field once per distinct key, since an attack can be hidden
 *       in the key instead of the value</li>
 *   <li>{@code header.<lowercase-name>} — one field per header</li>
 *   <li>{@code cookie.<name>} — one field per cookie</li>
 *   <li>{@code body} — the raw request body</li>
 * </ul>
 *
 * @author Worklet 1 Team
 */
public final class FieldExtractor {

    private FieldExtractor() {
        // Utility class; not instantiable.
    }

    /**
     * Extracts every scannable field from a request.
     *
     * @param request the request to break apart
     * @return the fields, in a fixed order (path, query, headers, cookies, body)
     */
    public static List<Field> extract(HttpRequest request) {
        List<Field> fields = new ArrayList<>();

        fields.add(new Field("path", toBytes(request.path())));

        for (Map.Entry<String, List<String>> entry : request.query().entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                fields.add(new Field("query." + name, toBytes(value)));
            }
            fields.add(new Field("query." + name + "#name", toBytes(name)));
        }

        for (Map.Entry<String, String> entry : request.headers().entrySet()) {
            String lowerName = entry.getKey().toLowerCase(Locale.ROOT);
            fields.add(new Field("header." + lowerName, toBytes(entry.getValue())));
        }

        for (Map.Entry<String, String> entry : request.cookies().entrySet()) {
            fields.add(new Field("cookie." + entry.getKey(), toBytes(entry.getValue())));
        }

        fields.add(new Field("body", request.body()));

        return fields;
    }

    private static byte[] toBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
