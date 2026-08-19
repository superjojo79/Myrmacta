package com.flowforge.domain.dossier.model;

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

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
