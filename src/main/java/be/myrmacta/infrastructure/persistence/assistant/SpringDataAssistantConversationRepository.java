package be.myrmacta.infrastructure.persistence.assistant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAssistantConversationRepository extends JpaRepository<AssistantConversationJpaEntity, UUID> {

    Optional<AssistantConversationJpaEntity> findByIdAndTenantIdAndOwnerId(UUID id, String tenantId, String ownerId);

    List<AssistantConversationJpaEntity> findByTenantIdAndOwnerIdOrderByUpdatedAtDesc(
            String tenantId, String ownerId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistantConversationJpaEntity c where c.id = :id and c.tenantId = :tenantId and c.ownerId = :ownerId")
    int deleteOwned(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("ownerId") String ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistantConversationJpaEntity c where c.tenantId = :tenantId and c.updatedAt < :cutoff")
    int deleteOlderThan(@Param("tenantId") String tenantId, @Param("cutoff") Instant cutoff);

    /** Mise à jour pure : ne ressuscite jamais une conversation supprimée. */
    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AssistantConversationJpaEntity c set c.updatedAt = :updatedAt, c.turns = :turns, c.inputTokens = :in, "
            + "c.outputTokens = :out where c.id = :id")
    int updateCounters(
            @Param("id") UUID id,
            @Param("updatedAt") Instant updatedAt,
            @Param("turns") int turns,
            @Param("in") long in,
            @Param("out") long out);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AssistantConversationJpaEntity c set c.busyUntil = :until "
            + "where c.id = :id and (c.busyUntil is null or c.busyUntil < :now)")
    int lock(@Param("id") UUID id, @Param("now") Instant now, @Param("until") Instant until);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AssistantConversationJpaEntity c set c.busyUntil = null where c.id = :id")
    int unlock(@Param("id") UUID id);

    /** Suppression refusée (0 ligne) tant qu'un tour est en cours. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistantConversationJpaEntity c where c.id = :id and c.tenantId = :tenantId and c.ownerId = :ownerId "
            + "and (c.busyUntil is null or c.busyUntil < :now)")
    int deleteIdle(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("ownerId") String ownerId, @Param("now") Instant now);
}
