package com.flowforge.domain.workflow.portal.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lien d'accès d'un tiers externe à <strong>une</strong> tâche {@code userTask} (TODO §68,
 * D-PORTAL-SCOPE). Immuable : chaque transition renvoie une nouvelle instance ; la persistance
 * applique la transition de façon conditionnelle sur le statut attendu
 * ({@code PortalLinkRepository#transition}), ce qui rend sûres la double soumission et les
 * renvois concurrents.
 *
 * <p>{@code tokenHash} : SHA-256 hexadécimal du jeton ; le jeton en clair n'est jamais stocké
 * (D-PORTAL-AUTH, D-PORTAL-INVITE-DELIVERY). {@code recipientEmail} est une donnée personnelle :
 * ne jamais la journaliser en clair ({@link PortalEmails#mask}).
 */
public record PortalLink(
        UUID id,
        String tenantId,
        UUID dossierId,
        String taskId,
        String processInstanceId,
        String schemaKey,
        String recipientEmail,
        String tokenHash,
        PortalLinkStatus status,
        PortalLinkStatusReason statusReason,
        int ttlDays,
        Instant expiresAt,
        int sendAttempts,
        Instant createdAt,
        Instant sentAt,
        Instant lastAccessedAt,
        Instant completedAt,
        Instant revokedAt,
        String createdBy,
        String revokedBy) {

    /** Préfixe d'identité externe tracée dans {@code submittedBy} / {@code addedBy}. */
    public static final String ACTOR_PREFIX = "portal:";

    public PortalLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Objects.requireNonNull(schemaKey, "schemaKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
        if (ttlDays <= 0) {
            throw new IllegalArgumentException("ttlDays must be positive");
        }
        if (status == PortalLinkStatus.ACTIVE && (tokenHash == null || expiresAt == null)) {
            throw new IllegalArgumentException("an ACTIVE link needs a token hash and an expiry");
        }
    }

    /** Lien créé à l'activation d'une tâche externe dont l'adresse est valide. */
    public static PortalLink pendingSend(
            UUID id, String tenantId, UUID dossierId, String taskId, String processInstanceId,
            String schemaKey, String recipientEmail, int ttlDays, Instant now, String createdBy) {
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        return new PortalLink(id, tenantId, dossierId, taskId, processInstanceId, schemaKey, recipientEmail,
                null, PortalLinkStatus.PENDING_SEND, null, ttlDays, null, 0, now, null, null, null, null,
                createdBy, null);
    }

    /** Lien qui n'a pas pu être émis (D-PORTAL-NO-ADDRESS) — l'instructeur le renverra. */
    public static PortalLink notSent(
            UUID id, String tenantId, UUID dossierId, String taskId, String processInstanceId,
            String schemaKey, String recipientEmail, int ttlDays, PortalLinkStatusReason reason, Instant now,
            String createdBy) {
        Objects.requireNonNull(reason, "reason");
        return new PortalLink(id, tenantId, dossierId, taskId, processInstanceId, schemaKey, recipientEmail,
                null, PortalLinkStatus.NOT_SENT, reason, ttlDays, null, 0, now, null, null, null, null,
                createdBy, null);
    }

    public String actorId() {
        return ACTOR_PREFIX + id;
    }

    public String maskedRecipient() {
        return PortalEmails.mask(recipientEmail);
    }

    /** Seul un lien {@code ACTIVE} non expiré donne accès au portail (contrôle paresseux). */
    public boolean isUsable(Instant now) {
        return status == PortalLinkStatus.ACTIVE && expiresAt != null && now.isBefore(expiresAt);
    }

    public PortalLink activated(String newTokenHash, Instant sentAtInstant) {
        requireStatus(PortalLinkStatus.PENDING_SEND);
        Objects.requireNonNull(newTokenHash, "newTokenHash");
        Instant expiry = sentAtInstant.plus(Duration.ofDays(ttlDays));
        return copy(newTokenHash, PortalLinkStatus.ACTIVE, null, expiry, sendAttempts + 1, sentAtInstant,
                lastAccessedAt, completedAt, revokedAt, revokedBy);
    }

    /** Échec d'envoi : nouvelle tentative, ou {@code NOT_SENT/SEND_FAILED} au-delà du plafond. */
    public PortalLink sendFailed(int maxAttempts) {
        requireStatus(PortalLinkStatus.PENDING_SEND);
        int attempts = sendAttempts + 1;
        if (attempts >= maxAttempts) {
            return copy(null, PortalLinkStatus.NOT_SENT, PortalLinkStatusReason.SEND_FAILED, null, attempts,
                    sentAt, lastAccessedAt, completedAt, revokedAt, revokedBy);
        }
        return copy(null, PortalLinkStatus.PENDING_SEND, null, null, attempts, sentAt, lastAccessedAt,
                completedAt, revokedAt, revokedBy);
    }

    public PortalLink accessed(Instant now) {
        requireStatus(PortalLinkStatus.ACTIVE);
        return copy(tokenHash, status, statusReason, expiresAt, sendAttempts, sentAt, now, completedAt,
                revokedAt, revokedBy);
    }

    public PortalLink completed(Instant now) {
        requireStatus(PortalLinkStatus.ACTIVE);
        return copy(tokenHash, PortalLinkStatus.COMPLETED, null, expiresAt, sendAttempts, sentAt, lastAccessedAt,
                now, revokedAt, revokedBy);
    }

    public PortalLink expired() {
        requireStatus(PortalLinkStatus.ACTIVE);
        return copy(tokenHash, PortalLinkStatus.EXPIRED, null, expiresAt, sendAttempts, sentAt, lastAccessedAt,
                completedAt, revokedAt, revokedBy);
    }

    public PortalLink revoked(Instant now, String by, PortalLinkStatusReason reason) {
        if (!status.isLive()) {
            throw new IllegalStateException("only a live link can be revoked, was " + status);
        }
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(reason, "reason");
        return copy(tokenHash, PortalLinkStatus.REVOKED, reason, expiresAt, sendAttempts, sentAt, lastAccessedAt,
                completedAt, now, by);
    }

    private void requireStatus(PortalLinkStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("expected status " + expected + " but was " + status);
        }
    }

    private PortalLink copy(
            String newTokenHash, PortalLinkStatus newStatus, PortalLinkStatusReason newReason, Instant newExpiresAt,
            int newSendAttempts, Instant newSentAt, Instant newLastAccessedAt, Instant newCompletedAt,
            Instant newRevokedAt, String newRevokedBy) {
        return new PortalLink(id, tenantId, dossierId, taskId, processInstanceId, schemaKey, recipientEmail,
                newTokenHash, newStatus, newReason, ttlDays, newExpiresAt, newSendAttempts, createdAt, newSentAt,
                newLastAccessedAt, newCompletedAt, newRevokedAt, createdBy, newRevokedBy);
    }
}
