package com.flowforge.domain.workflow.portal.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import java.util.List;
import java.util.UUID;

/**
 * Gestion instructeur (US-5, H6 tranché le 2026-09-24) : lecture avec VIEW sur le dossier,
 * envoi/renvoi/révocation avec EDIT sur le type du dossier (admins compris).
 */
public interface ManagePortalLinkUseCase {

    PortalLinks list(UUID dossierId, AccessContext.User actor);

    /** Révoque le lien vivant de la tâche et en émet un nouveau (adresse relue). */
    PortalLink send(UUID dossierId, String taskId, AccessContext.User actor);

    PortalLink revoke(UUID dossierId, UUID linkId, AccessContext.User actor);

    record PortalLinks(boolean canManage, List<PortalLink> links) {}
}
