package com.flowforge.domain.workflow.portal.port.out;

import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import java.util.Optional;
import java.util.UUID;

/**
 * Valeur brute de la source d'adresse d'une tâche externe : champ du dossier ou variable de
 * process, lu <strong>par nom littéral</strong>, jamais par évaluation d'expression (TODO §69).
 */
public interface PortalRecipientResolverPort {

    Optional<Object> resolve(PortalRecipientSource source, UUID dossierId, String processInstanceId);
}
