package be.myrmacta.infrastructure.persistence.mailroom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** TODO §150 (D-EBOX-JOURNAL) — message eBox pris en charge (changeset 053). Écrit en SQL natif ({@code on conflict}). */
@Entity
@Table(name = "ebox_message_journal")
class EboxMessageJournalJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "enterprise_number", nullable = false, length = 10)
    private String enterpriseNumber;

    @Column(name = "registry_id", nullable = false, length = 50)
    private String registryId;

    @Column(name = "message_id", nullable = false, length = 200)
    private String messageId;

    @Column(name = "partition_id", nullable = false, length = 100)
    private String partition;

    @Column(name = "connector_instance_id", nullable = false)
    private UUID connectorInstanceId;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    protected EboxMessageJournalJpaEntity() {}

    String messageId() {
        return messageId;
    }
}
