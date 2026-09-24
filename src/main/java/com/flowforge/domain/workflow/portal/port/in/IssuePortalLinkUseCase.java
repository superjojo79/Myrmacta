package com.flowforge.domain.workflow.portal.port.in;

import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalTaskDefinition;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Création du lien à l'activation d'une tâche externe (D-PORTAL-ISSUE, D-PORTAL-NO-ADDRESS). */
public interface IssuePortalLinkUseCase {

    /** Vide si aucun lien n'a été créé (tâche sans dossier, ou lien vivant déjà présent). */
    Optional<PortalLink> onExternalTaskCreated(ExternalTaskCreated event);

    record ExternalTaskCreated(
            String taskId, String processInstanceId, UUID dossierId, PortalTaskDefinition definition) {
        public ExternalTaskCreated {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(processInstanceId, "processInstanceId");
            Objects.requireNonNull(definition, "definition");
        }
    }
}
