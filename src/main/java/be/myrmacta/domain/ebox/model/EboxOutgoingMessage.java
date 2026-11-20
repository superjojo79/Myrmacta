package be.myrmacta.domain.ebox.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-MODEL) — message à publier. Le modèle commun est le format entreprise ({@code MessageToPublish} de
 * e-Box REST 3.8, réduit à ce que Myrmacta produit) ; {@link #citizen()} porte ce que le format citoyen ajoute et n'est admis que
 * pour un citoyen.
 *
 * <p>Règles de publication : une seule langue, une seule pièce principale, au plus 20 pièces et 100 Mo (spec 3.8). Un recommandé
 * (Registered Mail 1.1) n'a pas de corps et compte au plus une pièce principale et neuf annexes.
 */
public record EboxOutgoingMessage(
        EboxRecipient recipient,
        String senderApplicationId,
        String messageTypeId,
        EboxLanguage language,
        String subject,
        String body,
        List<EboxDocument> documents,
        boolean registered,
        boolean replyAuthorized,
        OffsetDateTime replyDueDate,
        OffsetDateTime expirationDate,
        String correlationId,
        EboxCitizenExtension citizen) {

    public static final int MAX_DOCUMENTS = 20;
    public static final int MAX_REGISTERED_ANNEXES = 9;
    public static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    /** Borne propre à Myrmacta : la spec publique ne fixe pas de longueur maximale de l'objet. */
    public static final int MAX_SUBJECT = 500;
    public static final int MAX_BODY = 100_000;
    private static final Pattern REFERENCE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    public EboxOutgoingMessage {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(language, "language");
        senderApplicationId = referenceId(senderApplicationId, "senderApplicationId");
        messageTypeId = referenceId(messageTypeId, "messageTypeId");
        subject = EboxTexts.line(subject, "subject", MAX_SUBJECT);
        body = body == null || body.isBlank() ? null : EboxTexts.text(body, "body", MAX_BODY);
        documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
        correlationId = EboxTexts.optionalLine(correlationId, "correlationId", 100);
        long mains = documents.stream().filter(EboxDocument::mainContent).count();
        if (mains != 1) {
            throw new IllegalArgumentException("a message needs exactly one main document (found " + mains + ")");
        }
        if (documents.size() > MAX_DOCUMENTS) {
            throw new IllegalArgumentException("a message has at most " + MAX_DOCUMENTS + " documents");
        }
        long total = documents.stream().mapToLong(EboxDocument::size).sum();
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("the documents of a message must not exceed 100 MB");
        }
        if (registered) {
            if (body != null) {
                throw new IllegalArgumentException("a registered mail has no body");
            }
            if (documents.size() > 1 + MAX_REGISTERED_ANNEXES) {
                throw new IllegalArgumentException("a registered mail has at most " + MAX_REGISTERED_ANNEXES + " annexes");
            }
        }
        if (replyDueDate != null && !replyAuthorized) {
            throw new IllegalArgumentException("replyDueDate requires replyAuthorized");
        }
        if (citizen != null && recipient.route() != EboxRoute.CITIZEN) {
            throw new IllegalArgumentException("the citizen extension only applies to a citizen recipient");
        }
    }

    public EboxRoute route() {
        return recipient.route();
    }

    public EboxDocument mainDocument() {
        return documents.stream().filter(EboxDocument::mainContent).findFirst().orElseThrow();
    }

    public List<EboxDocument> annexes() {
        return documents.stream().filter(document -> !document.mainContent()).toList();
    }

    public static Builder builder(EboxRecipient recipient, String senderApplicationId, String messageTypeId, EboxLanguage language,
            String subject) {
        return new Builder(recipient, senderApplicationId, messageTypeId, language, subject);
    }

    private static String referenceId(String value, String name) {
        String text = value == null ? "" : value.strip();
        if (!REFERENCE_ID.matcher(text).matches()) {
            throw new IllegalArgumentException(name + " must be 1 to 100 characters among letters, digits and ._:-");
        }
        return text;
    }

    /** Construction pas à pas ; {@link #build()} applique toutes les règles. */
    public static final class Builder {
        private final EboxRecipient recipient;
        private final String senderApplicationId;
        private final String messageTypeId;
        private final EboxLanguage language;
        private final String subject;
        private final List<EboxDocument> documents = new ArrayList<>();
        private String body;
        private boolean registered;
        private boolean replyAuthorized;
        private OffsetDateTime replyDueDate;
        private OffsetDateTime expirationDate;
        private String correlationId;
        private EboxCitizenExtension citizen;

        private Builder(EboxRecipient recipient, String senderApplicationId, String messageTypeId, EboxLanguage language,
                String subject) {
            this.recipient = recipient;
            this.senderApplicationId = senderApplicationId;
            this.messageTypeId = messageTypeId;
            this.language = language;
            this.subject = subject;
        }

        public Builder document(EboxDocument document) {
            documents.add(Objects.requireNonNull(document, "document"));
            return this;
        }

        public Builder body(String value) {
            this.body = value;
            return this;
        }

        public Builder registered(boolean value) {
            this.registered = value;
            return this;
        }

        public Builder replyAuthorized(boolean value) {
            this.replyAuthorized = value;
            return this;
        }

        public Builder replyDueDate(OffsetDateTime value) {
            this.replyDueDate = value;
            return this;
        }

        public Builder expirationDate(OffsetDateTime value) {
            this.expirationDate = value;
            return this;
        }

        public Builder correlationId(String value) {
            this.correlationId = value;
            return this;
        }

        public Builder citizen(EboxCitizenExtension value) {
            this.citizen = value;
            return this;
        }

        public EboxOutgoingMessage build() {
            return new EboxOutgoingMessage(recipient, senderApplicationId, messageTypeId, language, subject, body, documents,
                    registered, replyAuthorized, replyDueDate, expirationDate, correlationId, citizen);
        }
    }
}
