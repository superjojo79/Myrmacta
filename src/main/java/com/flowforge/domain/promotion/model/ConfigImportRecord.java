package com.flowforge.domain.promotion.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** TODO §83 (D4) — trace d'une application de bundle (jamais de secret : seuls des compteurs et le rapport). */
public record ConfigImportRecord(
        UUID id,
        String tenantId,
        String performedBy,
        Instant performedAt,
        String channel,
        String bundleSha256,
        int formatVersion,
        String sourceLabel,
        int applied,
        int unchanged,
        int skipped,
        int failed,
        String reportJson) {

    public ConfigImportRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(performedBy, "performedBy");
        Objects.requireNonNull(performedAt, "performedAt");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(bundleSha256, "bundleSha256");
        sourceLabel = sourceLabel == null ? "" : sourceLabel;
        reportJson = reportJson == null ? "[]" : reportJson;
    }
}
