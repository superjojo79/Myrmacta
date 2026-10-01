package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import java.time.Instant;

/**
 * TODO §82 (D6) — écriture d'un brouillon par un canal automatisé (MCP, assistant) : mêmes contrôles qu'un déploiement
 * (parseur, liste blanche, références), mais rien n'est déployé ni appliqué avant la publication.
 */
public interface ProposeDraftUseCase {

    /**
     * @param config configuration de workflow à créer ou mettre à jour à la publication
     * @param replaceExisting autorise l'écrasement d'un brouillon d'un autre auteur ou d'une autre provenance
     * @throws com.flowforge.domain.workflow.model.DraftConflictException sinon
     */
    Proposal proposeBpmn(
            AccessContext.User actor,
            Provenance provenance,
            String processDefinitionKey,
            String xml,
            ProposedWorkflowConfig config,
            boolean replaceExisting);

    Proposal proposeDmn(
            AccessContext.User actor,
            Provenance provenance,
            String decisionKey,
            String xml,
            String note,
            boolean replaceExisting);

    /** Charge une version déjà publiée d'une table de décision dans un brouillon (remise en ligne à relire). */
    Proposal proposeDmnFromVersion(
            AccessContext.User actor, Provenance provenance, String decisionKey, int version, boolean replaceExisting);

    /** Validation seule (aucune écriture) : mêmes contrôles que {@code proposeBpmn}. */
    void validateBpmn(AccessContext.User actor, String processDefinitionKey, String xml, ProposedWorkflowConfig config);

    void validateDmn(AccessContext.User actor, String decisionKey, String xml);

    record Proposal(DraftArtifactType type, String key, Instant updatedAt, boolean replacedExistingDraft) {}
}
