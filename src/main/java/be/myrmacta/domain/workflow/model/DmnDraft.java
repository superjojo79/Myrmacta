package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A saved-but-not-yet-deployed DMN decision table for a given decision key. One record per
 * {@code (tenantId, decisionKey)} — overwritten in place on every save, no version history
 * (mirrors {@link BpmnDraft} / D-EDIT-DRAFT-MODEL).
 *
 * <p>TODO §82 (D6) : {@code provenance} (éditeur, agent MCP, assistant) et {@code note} (ex. « remise en ligne de la version 3 »).
 */
public record DmnDraft(
        String tenantId,
        String decisionKey,
        String xml,
        Instant updatedAt,
        String updatedBy,
        Provenance provenance,
        String note,
        String pilotUserId) {

    public DmnDraft {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(decisionKey, "decisionKey");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        provenance = provenance == null ? Provenance.UI : provenance;
    }

    /** Brouillon de l'éditeur (comportement historique). */
    public DmnDraft(String tenantId, String decisionKey, String xml, Instant updatedAt, String updatedBy) {
        this(tenantId, decisionKey, xml, updatedAt, updatedBy, Provenance.UI, null, null);
    }
}
