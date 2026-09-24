package com.flowforge.domain.workflow.portal.port.out;

import com.flowforge.domain.workflow.portal.model.PortalLink;

/**
 * Envoi de l'invitation au tiers (D-PORTAL-INVITE-DELIVERY) : e-mail direct, <strong>pas</strong>
 * l'outbox de notification, qui stockerait le lien en clair en base.
 */
public interface PortalInviteSenderPort {

    /** Lève une exception si l'envoi échoue (le job retentera avec un nouveau jeton). */
    void send(PortalLink link, String token, InviteContext context);

    record InviteContext(String taskName, String dossierReference, java.time.Instant expiresAt) {}
}
