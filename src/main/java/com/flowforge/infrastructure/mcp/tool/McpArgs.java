package com.flowforge.infrastructure.mcp.tool;

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
}
