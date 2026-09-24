package com.flowforge.infrastructure.web.portal;

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
        String revokedBy) {

    static PortalLinkResponse from(PortalLink link, boolean fullRecipient) {
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
                link.revokedBy());
    }

    record ListResponse(boolean canManage, List<PortalLinkResponse> links) {}
}
