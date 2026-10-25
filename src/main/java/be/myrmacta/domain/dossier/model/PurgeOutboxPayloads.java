package be.myrmacta.domain.dossier.model;

import java.time.Instant;
import java.util.UUID;

/** Minimal JSON payload helpers for {@code purge_outbox.payload_json} (no Jackson in domain). */
public final class PurgeOutboxPayloads {

    private PurgeOutboxPayloads() {}

    public static String json(String tenantId, UUID dossierId, String typeCode, Instant purgeEligibleAt) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
        sb.append("\"dossierId\":\"").append(dossierId).append("\",");
        sb.append("\"typeCode\":\"").append(escape(typeCode)).append("\",");
        sb.append("\"purgeEligibleAt\":\"").append(purgeEligibleAt).append("\"");
        sb.append('}');
        return sb.toString();
    }

    public static Instant parseEligibleAt(String payloadJson) {
        String value = extract(payloadJson, "purgeEligibleAt");
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        return Instant.parse(value);
    }

    public static String parseTenantId(String payloadJson) {
        return extract(payloadJson, "tenantId");
    }

    private static String extract(String json, String field) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    /**
     * Escapes {@code raw} for embedding in a JSON string per RFC 8259 — {@code \} and {@code "},
     * plus every control character (0x00-0x1F), which the spec requires to be escaped and which
     * a raw, unescaped occurrence would otherwise turn into a syntactically invalid JSON document
     * (and, for the retention job's single-transaction batch, a silent tenant-wide stall).
     */
    private static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
