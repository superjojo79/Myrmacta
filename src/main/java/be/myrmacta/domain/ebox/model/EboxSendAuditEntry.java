package be.myrmacta.domain.ebox.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) — une ligne du journal d'audit des envois, jamais modifiée ni supprimée. Champs inspirés des
 * règles ATREQ01 à 04 de l'eBox (registre §155) : horodatage à la milliseconde, trace de l'appel (méthode, URL, code, client
 * OAuth, mandat, empreinte du jeton), pièces réduites à leur taille et leur empreinte (minimalité), identifiant rendu, et en plus
 * l'état du consentement constaté et la voie retenue. Le NISS d'un citoyen n'est jamais écrit en clair : l'adaptateur de
 * persistance le remplace par une empreinte à clé.
 *
 * @param publication nulle quand rien n'a été publié (voie de secours seule)
 * @param fallbackChannel voie de secours demandée par le process ; utilisée si {@link #delivery()} la réclame
 */
public record EboxSendAuditEntry(
        UUID id,
        String tenantId,
        Instant recordedAt,
        UUID senderInstanceId,
        EboxRoute route,
        EboxRecipient recipient,
        String idempotencyKey,
        UUID dossierId,
        String correlationId,
        String senderApplicationId,
        String messageTypeId,
        EboxLanguage language,
        boolean registered,
        List<DocumentTrace> documents,
        EboxConsent consent,
        EboxDelivery delivery,
        FallbackChannel fallbackChannel,
        Outcome outcome,
        EboxPublication publication) {

    public enum Outcome {
        PUBLISHED,
        DUPLICATE,
        UNCERTAIN,
        FALLBACK
    }

    /** Une pièce, réduite à ce que l'audit demande : taille, empreinte SHA-256, rang (principale ou annexe). */
    public record DocumentTrace(long size, String sha256, boolean mainContent) {

        private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

        public DocumentTrace {
            if (size <= 0 || sha256 == null || !HEX64.matcher(sha256).matches()) {
                throw new IllegalArgumentException("a document trace has a positive size and a SHA-256 hex digest");
            }
        }

        public static DocumentTrace of(EboxDocument document) {
            return new DocumentTrace(document.size(), document.sha256Hex(), document.mainContent());
        }
    }

    /**
     * Ce que le journal rend pour une clé d'idempotence déjà vue.
     *
     * @param sameRecipient faux si la clé a servi pour un autre destinataire
     */
    public record Summary(Outcome outcome, EboxDelivery delivery, FallbackChannel fallbackChannel, String providerMessageId,
            boolean sameRecipient) {

        public Summary {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(delivery, "delivery");
            Objects.requireNonNull(fallbackChannel, "fallbackChannel");
        }
    }

    public EboxSendAuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(senderInstanceId, "senderInstanceId");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(language, "language");
        documents = List.copyOf(documents);
        Objects.requireNonNull(consent, "consent");
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(fallbackChannel, "fallbackChannel");
        Objects.requireNonNull(outcome, "outcome");
        if (recipient.route() != route) {
            throw new IllegalArgumentException("recipient does not match the route");
        }
        if ((outcome == Outcome.FALLBACK) != (publication == null)) {
            throw new IllegalArgumentException("only a fallback entry has no publication");
        }
        if (publication != null && !publication.status().name().equals(outcome.name())) {
            throw new IllegalArgumentException("outcome does not match the publication status");
        }
    }

    public Summary summary(EboxRecipient candidate) {
        return new Summary(outcome, delivery, fallbackChannel, publication == null ? null : publication.providerMessageId(),
                recipient.equals(candidate));
    }
}
