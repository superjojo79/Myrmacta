package be.myrmacta.infrastructure.persistence.mailroom;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMailMessageIndexRepository extends JpaRepository<MailMessageIndexJpaEntity, UUID> {

    /**
     * Inscrit l'entrée, atomiquement. Un identifiant REÇU déjà connu n'est pas réinscrit (revue de clôture de §113) : un
     * Message-ID est choisi par l'expéditeur ; un tiers qui réutilise celui d'un fil ne doit pas masquer l'entrée légitime.
     * Un identifiant ENVOYÉ (posé par la plateforme) est toujours inscrit. {@code on conflict do nothing} plutôt qu'une
     * violation de clé : sous PostgreSQL une violation avorte la transaction de l'appelant. SQL natif : ni
     * {@code insert … select} ni {@code on conflict} n'existent en JPQL.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into mail_message_index (id, tenant_id, kind, message_key, direction, dossier_id, counterpart, recorded_at)
            select cast(:id as uuid), cast(:tenantId as varchar), cast(:kind as varchar), cast(:messageKey as varchar),
                   cast(:direction as varchar), cast(:dossierId as uuid), cast(:counterpart as varchar), cast(:recordedAt as timestamptz)
            where cast(:direction as varchar) = 'OUT' or not exists (
                select 1 from mail_message_index where tenant_id = :tenantId and kind = :kind and message_key = :messageKey)
            on conflict (tenant_id, kind, message_key, direction, dossier_id) do nothing
            """)
    int recordUnlessShadowing(
            @Param("id") UUID id,
            @Param("tenantId") String tenantId,
            @Param("kind") String kind,
            @Param("messageKey") String messageKey,
            @Param("direction") String direction,
            @Param("dossierId") UUID dossierId,
            @Param("counterpart") String counterpart,
            @Param("recordedAt") Instant recordedAt);

    /** L'entrée ENVOYÉE d'abord, puis la plus ancienne. */
    @Query("""
            select m from MailMessageIndexJpaEntity m
            where m.tenantId = :tenantId and m.kind = :kind and m.messageKey = :messageKey
            order by case when m.direction = 'OUT' then 0 else 1 end, m.recordedAt asc
            """)
    List<MailMessageIndexJpaEntity> findByKey(
            @Param("tenantId") String tenantId, @Param("kind") String kind, @Param("messageKey") String messageKey, Limit limit);

    @Query("select m from MailMessageIndexJpaEntity m where m.tenantId = :tenantId and m.dossierId = :dossierId order by m.recordedAt desc")
    List<MailMessageIndexJpaEntity> findNewestOfDossier(@Param("tenantId") String tenantId, @Param("dossierId") UUID dossierId, Limit limit);
}
