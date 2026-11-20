package be.myrmacta.infrastructure.persistence.ebox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** TODO §155 (D-EBOX-SEND-REGISTERED) — clé de recommandé réservée (changeset 055). Écrite en SQL natif ({@code on conflict}). */
@Entity
@Table(name = "ebox_send_reservation")
class EboxSendReservationJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    protected EboxSendReservationJpaEntity() {}
}
