package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * MCP-facing view of a redacted {@link Dossier} (DD-NO-FIELDS-READ) — mirrors
 * {@code infrastructure.web.dossier.DossierApiDtos.DossierResponse} but lives in this package so
 * {@code GetDossierTool} never has to call {@code Dossier.fields()} itself
 * (ArchitectureRulesTest.DOSSIER_FIELDS_READ_ALLOWLIST).
 */
public record DossierMcpView(
        UUID id,
        String tenantId,
        String reference,
        String dossierTypeCode,
        String label,
        DossierStatus status,
        Map<String, Object> fields,
        UUID currentWorkflowLinkId,
        long version,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        String closedBy,
        Instant closedAt,
        Instant purgeEligibleAt) {

    public static DossierMcpView from(Dossier dossier) {
        return new DossierMcpView(
                dossier.id(),
                dossier.tenantId(),
                dossier.reference(),
                dossier.dossierTypeCode(),
                dossier.label(),
                dossier.status(),
                dossier.fields(),
                dossier.currentWorkflowLinkId(),
                dossier.version(),
                dossier.createdBy(),
                dossier.createdAt(),
                dossier.updatedBy(),
                dossier.updatedAt(),
                dossier.closedBy(),
                dossier.closedAt(),
                dossier.purgeEligibleAt());
    }
}
