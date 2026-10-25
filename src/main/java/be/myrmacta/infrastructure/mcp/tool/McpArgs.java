package be.myrmacta.infrastructure.mcp.tool;

import java.util.Map;

/** Small helpers for tools that inspect a raw {@code McpSchema.CallToolRequest.arguments()} map. */
final class McpArgs {

    private McpArgs() {}

    static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return s;
    }

    static boolean requireBoolean(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Boolean b)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return b;
    }

    static String requireNonBlank(String raw, String key) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return raw.trim();
    }

    static java.util.UUID requireUuid(String raw, String key) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        try {
            return java.util.UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(key + " must be a UUID");
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> optionalMap(Object value, String key) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new IllegalArgumentException(key + " must be an object");
            }
            copy.put(name, entry.getValue());
        }
        return Map.copyOf(copy);
    }
}
