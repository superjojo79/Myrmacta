package be.myrmacta.infrastructure.persistence.mailroom;

import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** TODO §113 (D-MAILROOM-INDEX) — identifiant d'un e-mail reçu ou envoyé, rattaché à un dossier (changeset 043). Supprimé avec le dossier. */
@Entity
@Table(name = "mail_message_index")
class MailMessageIndexJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "message_key", nullable = false, length = 250)
    private String messageKey;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(length = 320)
    private String counterpart;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected MailMessageIndexJpaEntity() {}

    MailIndexEntry toDomain() {
        return new MailIndexEntry(tenantId, MailIndexEntry.Kind.valueOf(kind), messageKey, MailIndexEntry.Direction.valueOf(direction),
                dossierId, counterpart, recordedAt);
    }
}
