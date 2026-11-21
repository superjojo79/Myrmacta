package be.myrmacta.infrastructure.persistence.mailroom;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEboxMessageJournalRepository extends JpaRepository<EboxMessageJournalJpaEntity, UUID> {

    @Query("""
            select j.messageId from EboxMessageJournalJpaEntity j
            where j.tenantId = :tenantId and j.enterpriseNumber = :enterpriseNumber and j.registryId = :registryId
              and j.messageId in :messageIds
            """)
    List<String> findTaken(
            @Param("tenantId") String tenantId,
            @Param("enterpriseNumber") String enterpriseNumber,
            @Param("registryId") String registryId,
            @Param("messageIds") Collection<String> messageIds);

    Optional<EboxMessageJournalJpaEntity> findByTenantIdAndEnterpriseNumberAndRegistryIdAndMessageId(
            String tenantId, String enterpriseNumber, String registryId, String messageId);

    /**
     * {@code on conflict do nothing} plutôt qu'une violation de clé : sous PostgreSQL une violation avorte la transaction de
     * l'appelant. Rend 0 si le message était déjà inscrit.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into ebox_message_journal
                (id, tenant_id, enterprise_number, registry_id, message_id, partition_id, connector_instance_id, taken_at)
            values (cast(:id as uuid), :tenantId, :enterpriseNumber, :registryId, :messageId, :partition,
                    cast(:instanceId as uuid), cast(:takenAt as timestamptz))
            on conflict (tenant_id, enterprise_number, registry_id, message_id) do nothing
            """)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") String tenantId,
            @Param("enterpriseNumber") String enterpriseNumber,
            @Param("registryId") String registryId,
            @Param("messageId") String messageId,
            @Param("partition") String partition,
            @Param("instanceId") UUID instanceId,
            @Param("takenAt") Instant takenAt);
}
