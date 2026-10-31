package be.myrmacta.domain.mailroom.model;

import java.util.Objects;

/**
 * TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — workflow « réponse » d'un type de dossier : ce qui démarre quand une réponse rejoint
 * un dossier de ce type. {@code SUR_DOSSIER} : sur le dossier lui-même, si aucun process n'y est actif ; {@code DOSSIER_ENFANT} :
 * dans un dossier enfant de type {@code childTypeCode}, en parallèle du process métier.
 */
public record ReplyWorkflowSetting(String tenantId, String dossierTypeCode, Mode mode, String workflowKey, String childTypeCode) {

    public enum Mode { SUR_DOSSIER, DOSSIER_ENFANT }

    public ReplyWorkflowSetting {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        if (mode == null) {
            throw new IllegalArgumentException("mode is required: SUR_DOSSIER or DOSSIER_ENFANT");
        }
        workflowKey = workflowKey == null ? "" : workflowKey.strip();
        if (workflowKey.isEmpty()) {
            throw new IllegalArgumentException("workflowKey is required");
        }
        childTypeCode = childTypeCode == null || childTypeCode.isBlank() ? null : childTypeCode.strip();
        if (mode == Mode.DOSSIER_ENFANT && childTypeCode == null) {
            throw new IllegalArgumentException("childTypeCode is required with DOSSIER_ENFANT");
        }
        if (mode == Mode.SUR_DOSSIER) {
            childTypeCode = null;
        }
    }
}
