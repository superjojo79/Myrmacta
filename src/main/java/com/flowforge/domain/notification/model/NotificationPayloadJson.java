package com.flowforge.domain.notification.model;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal JSON builder for outbox payload snapshots. Domain must not depend on Jackson
 * or Spring (ArchUnit). Supports String / Number / Boolean / null / nested Map / Collection.
 */
public final class NotificationPayloadJson {

    private NotificationPayloadJson() {}

    public static String email(String to, String subject, String body) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        appendField(sb, "to", to, true);
        appendField(sb, "subject", subject, false);
        appendField(sb, "body", body, false);
        sb.append('}');
        return sb.toString();
    }

    public static String webhook(UUID webhookConfigId, Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        appendField(sb, "webhookConfigId", webhookConfigId.toString(), true);
        sb.append(',');
        quote(sb, "payload");
        sb.append(':');
        writeValue(sb, payload == null ? Map.of() : payload);
        sb.append('}');
        return sb.toString();
    }

    /** Serializes just the payload map, no channel-specific wrapper — used for {@code user_notification}. */
    public static String plain(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder(64);
        writeValue(sb, payload == null ? Map.of() : payload);
        return sb.toString();
    }

    /**
     * {@code inboxId} is the {@code user_notification} row id, not the outbox row id — the relay
     * uses it as the SSE event id so a client can mark that exact history row read (D-NOTIF-SSE-ID).
     */
    public static String browser(String userId, Map<String, Object> payload, UUID inboxId) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        appendField(sb, "userId", userId, true);
        appendField(sb, "inboxId", inboxId.toString(), false);
        sb.append(',');
        quote(sb, "payload");
        sb.append(':');
        writeValue(sb, payload == null ? Map.of() : payload);
        sb.append('}');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean first) {
        if (!first) {
            sb.append(',');
        }
        quote(sb, key);
        sb.append(':');
        quote(sb, value);
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String s) {
            quote(sb, s);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                writeValue(sb, entry.getValue());
            }
            sb.append('}');
            return;
        }
        if (value instanceof Collection<?> collection) {
            sb.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
            return;
        }
        quote(sb, String.valueOf(value));
    }

    private static void quote(StringBuilder sb, String raw) {
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
