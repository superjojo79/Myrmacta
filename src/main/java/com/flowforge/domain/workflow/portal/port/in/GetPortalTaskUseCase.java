package com.flowforge.domain.workflow.portal.port.in;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.workflow.portal.model.PortalCredential;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import java.time.Instant;
import java.util.List;

/** Vue minimale d'une tâche externe (D-PORTAL-DATA) : rien d'autre du dossier. */
public interface GetPortalTaskUseCase {

    PortalTaskView get(PortalCredential credential);

    default PortalTaskView get(String token) {
        return get(PortalCredential.of(token, null));
    }

    /**
     * TODO §104 (T3b) — {@code identityRequired} sans identité vérifiée : vue réduite (référence, nom de la tâche et
     * fournisseurs seulement). {@code verifiedName} : nom de la personne authentifiée, sinon nul.
     */
    record PortalTaskView(
            String dossierReference,
            String taskName,
            String instructions,
            String formLabel,
            List<FieldDefinition> fields,
            Instant expiresAt,
            List<DossierElement> ownAttachments,
            boolean identityRequired,
            List<PortalIdentityProviderPort.ProviderOption> identityProviders,
            String verifiedName) {

        public PortalTaskView(
                String dossierReference, String taskName, String instructions, String formLabel,
                List<FieldDefinition> fields, Instant expiresAt, List<DossierElement> ownAttachments) {
            this(dossierReference, taskName, instructions, formLabel, fields, expiresAt, ownAttachments, false, List.of(),
                    null);
        }
    }
}
