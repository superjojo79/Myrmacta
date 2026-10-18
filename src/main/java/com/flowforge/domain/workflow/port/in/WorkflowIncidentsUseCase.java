package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.workflow.model.TenantIncident;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import java.util.List;
import java.util.UUID;

/** TODO §94 — échecs des jobs d'une exécution, d'un dossier ou du tenant, et rejeu d'un incident. */
public interface WorkflowIncidentsUseCase {

    /**
     * Participants, éditeurs du dossier et TA/WA ; message réservé à TA/WA (D-INCIDENT-MESSAGE). Un simple lecteur du dossier
     * reçoit une liste vide (pas de refus journalisé à chaque ouverture de fiche).
     */
    List<WorkflowIncident> listForLink(UUID linkId, AccessContext.User actor);

    /** TA/WA ; incident (dead letter) de l'exécution du lien seulement. */
    void retry(UUID linkId, String jobId, AccessContext.User actor);

    /** TA/WA ; échecs du tenant rattachés à un dossier, bornés (D-INCIDENT-SCAN). */
    TenantIncidentPage listForTenant(AccessContext.User actor, PageQuery query);

    /** @param truncated vrai si la borne de lecture a été atteinte : d'autres échecs existent au-delà */
    record TenantIncidentPage(com.flowforge.domain.shared.PageResult<TenantIncident> page, boolean truncated) {}
}
