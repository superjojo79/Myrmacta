package be.myrmacta.infrastructure.persistence.ebox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** TODO §155 (D-EBOX-SEND-REGISTERED) — réservations de clés de recommandé. */
interface SpringDataEboxSendReservationRepository extends Repository<EboxSendReservationJpaEntity, UUID> {

    /**
     * {@code on conflict do nothing} plutôt qu'une violation de clé : sous PostgreSQL une violation avorte la transaction. Rend 0
     * si la clé était déjà réservée.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into ebox_send_reservation (id, tenant_id, idempotency_key, reserved_at)
            values (cast(:id as uuid), :tenantId, :idempotencyKey, cast(:at as timestamptz))
            on conflict (tenant_id, idempotency_key) do nothing
            """)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") String tenantId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("at") Instant at);

    @Modifying
    @Query("delete from EboxSendReservationJpaEntity r where r.tenantId = :tenantId and r.idempotencyKey = :idempotencyKey")
    int release(@Param("tenantId") String tenantId, @Param("idempotencyKey") String idempotencyKey);
}
