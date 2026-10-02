package com.flowforge.domain.connector.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ce qu'un connecteur TRIGGER demande : créer (ou retrouver) un dossier et démarrer un processus. TODO §87 (D3c) :
 * {@code attachments} — pièces jointes d'un message entrant, rattachées au dossier après contrôles (types, contenu, antivirus) ;
 * {@code createWhenReferenceUnknown} — une référence de dossier introuvable (faute de frappe dans un objet d'e-mail) crée un nouveau
 * dossier au lieu de perdre le message.
 */
public record WorkflowTriggerEvent(
        String processKey,
        String dossierTypeCode,
        Map<String, Object> dossierFields,
        Map<String, Object> variables,
        String existingDossierReference,
        List<InboundAttachment> attachments,
        boolean createWhenReferenceUnknown
) {
    public WorkflowTriggerEvent {
        Objects.requireNonNull(processKey, "processKey");
        dossierFields = dossierFields == null ? Map.of() : Map.copyOf(dossierFields);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public WorkflowTriggerEvent(
            String processKey,
            String dossierTypeCode,
            Map<String, Object> dossierFields,
            Map<String, Object> variables,
            String existingDossierReference,
            List<InboundAttachment> attachments) {
        this(processKey, dossierTypeCode, dossierFields, variables, existingDossierReference, attachments, false);
    }

    /** Sans pièce jointe : tout ce qui existait avant D3c. */
    public WorkflowTriggerEvent(
            String processKey,
            String dossierTypeCode,
            Map<String, Object> dossierFields,
            Map<String, Object> variables,
            String existingDossierReference) {
        this(processKey, dossierTypeCode, dossierFields, variables, existingDossierReference, List.of());
    }
}
