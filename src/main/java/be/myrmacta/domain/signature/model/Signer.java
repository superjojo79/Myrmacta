package be.myrmacta.domain.signature.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Signataire d'une demande (TODO §72). Immuable ; chaque transition renvoie une nouvelle instance
 * et la persistance l'applique par compare-and-set sur {@code version}.
 *
 * <p>{@code email} (externe) est une donnée personnelle : ne jamais la journaliser en clair
 * ({@link #maskedEmail()}). {@code tokenHash} est le SHA-256 du jeton, jamais le jeton.
 */
public record Signer(
        UUID id,
        String tenantId,
        UUID requestId,
        int position,
        SignerKind kind,
        boolean required,
        String userId,
        String displayName,
        String email,
        String tokenHash,
        SignerStatus status,
        Instant inviteSentAt,
        int inviteAttempts,
        Instant decidedAt,
        String declineReason,
        long version) {

    /** Préfixe d'identité externe tracée (D-SIGNATURE-ACTOR). */
    public static final String ACTOR_PREFIX = "signature:";

    public static final int MAX_DECLINE_REASON = 500;

    public Signer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        if (kind == SignerKind.INTERNAL && (userId == null || userId.isBlank())) {
            throw new IllegalArgumentException("an INTERNAL signer needs a userId");
        }
        if (kind == SignerKind.EXTERNAL && (email == null || email.isBlank())) {
            throw new IllegalArgumentException("an EXTERNAL signer needs an email");
        }
        if (declineReason != null && declineReason.length() > MAX_DECLINE_REASON) {
            throw new IllegalArgumentException("declineReason too long");
        }
    }

    public String actorId() {
        return kind == SignerKind.INTERNAL ? userId : ACTOR_PREFIX + id;
    }

    /** Identité normalisée servant à détecter un doublon (A5, D-SIGNATURE-DUPLICATE). */
    public String identityKey() {
        return kind == SignerKind.INTERNAL
                ? "user:" + userId.trim().toLowerCase(java.util.Locale.ROOT)
                : "mail:" + email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String maskedEmail() {
        return email == null ? null : be.myrmacta.domain.workflow.portal.model.PortalEmails.mask(email);
    }

    public Signer withStatus(SignerStatus next) {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, tokenHash,
                next, inviteSentAt, inviteAttempts, decidedAt, declineReason, version);
    }

    public Signer decided(SignerStatus next, Instant at, String reason) {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, tokenHash,
                next, inviteSentAt, inviteAttempts, at, reason, version);
    }

    public Signer withToken(String newTokenHash) {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email,
                newTokenHash, status, inviteSentAt, inviteAttempts, decidedAt, declineReason, version);
    }

    public Signer invited(Instant at) {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, tokenHash,
                status, at, inviteAttempts, decidedAt, declineReason, version);
    }

    /** Renvoi : l'ancien jeton est invalidé (plus de hash), l'invitation redevient à envoyer. */
    public Signer invitationReset() {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, null,
                status, null, 0, decidedAt, declineReason, version);
    }

    /** Envoi échoué : le jeton réservé est abandonné, l'invitation redevient à envoyer (tentative comptée). */
    public Signer invitationFailed(int attempts) {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, null,
                status, null, attempts, decidedAt, declineReason, version);
    }

    public Signer withInviteAttempts(int attempts) {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, tokenHash,
                status, inviteSentAt, attempts, decidedAt, declineReason, version);
    }

    /** Copie portant la version relue en base (utilisée par les adaptateurs de persistance). */
    public Signer withVersion(long newVersion) {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, tokenHash,
                status, inviteSentAt, inviteAttempts, decidedAt, declineReason, newVersion);
    }
}
