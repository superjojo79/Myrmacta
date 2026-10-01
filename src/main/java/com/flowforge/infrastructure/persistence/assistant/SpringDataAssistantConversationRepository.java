package com.flowforge.infrastructure.persistence.assistant;

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
}
