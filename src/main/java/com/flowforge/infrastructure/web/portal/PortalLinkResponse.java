package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.workflow.portal.model.IdentityVerification;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TODO §68 US-5 — vue instructeur d'un lien portail. L'adresse complète n'est renvoyée qu'aux
 * utilisateurs pouvant gérer le lien (EDIT) ; une lecture VIEW ne voit que l'adresse masquée (la
 * source peut être un champ marqué sensible). Le hash du jeton n'est jamais exposé.
 */
record PortalLinkResponse(
        UUID id,
        String taskId,
        String status,
        String statusReason,
        String recipient,
        int ttlDays,
        Instant createdAt,
        Instant sentAt,
        Instant expiresAt,
        Instant lastAccessedAt,
        Instant completedAt,
        Instant revokedAt,
        String createdBy,
        String revokedBy,
        IdentityResponse identity) {

    static PortalLinkResponse from(PortalLink link, boolean fullRecipient) {
        return from(link, fullRecipient, null);
    }

    /**
     * TODO §103 (T8) — trace de l'authentification forte, jamais le NRN. Le nom vérifié, donnée personnelle du tiers,
     * suit la même règle que l'adresse : réservé à qui peut gérer le lien.
     */
    static PortalLinkResponse from(PortalLink link, boolean fullRecipient, IdentityVerification identity) {
        return new PortalLinkResponse(
                link.id(),
                link.taskId(),
                link.status().name(),
                link.statusReason() == null ? null : link.statusReason().name(),
                fullRecipient ? link.recipientEmail() : (link.recipientEmail() == null ? null : link.maskedRecipient()),
                link.ttlDays(),
                link.createdAt(),
                link.sentAt(),
                link.expiresAt(),
                link.lastAccessedAt(),
                link.completedAt(),
                link.revokedAt(),
                link.createdBy(),
                link.revokedBy(),
                identity == null ? null : IdentityResponse.from(identity, fullRecipient));
    }

    record IdentityResponse(
            String provider, String level, String givenName, String familyName, String result, Instant verifiedAt) {
        static IdentityResponse from(IdentityVerification identity, boolean withName) {
            return new IdentityResponse(identity.provider(), identity.level().name(),
                    withName ? identity.givenName() : null, withName ? identity.familyName() : null,
                    identity.result().name(), identity.verifiedAt());
        }
    }

    record ListResponse(boolean canManage, List<PortalLinkResponse> links) {}
}
