package com.flowforge.infrastructure.persistence.assistant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAssistantActionRepository extends JpaRepository<AssistantActionJpaEntity, UUID> {

    List<AssistantActionJpaEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AssistantActionJpaEntity a set a.status = :next, a.outcome = :outcome, a.resolvedAt = :at "
            + "where a.id = :id and a.status = :expected")
    int transition(
            @Param("id") UUID id,
            @Param("expected") String expected,
            @Param("next") String next,
            @Param("outcome") String outcome,
            @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistantActionJpaEntity a where a.conversationId = :id")
    int deleteByConversation(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistantActionJpaEntity a where a.conversationId in "
            + "(select c.id from AssistantConversationJpaEntity c where c.tenantId = :tenantId and c.updatedAt < :cutoff)")
    int deleteOlderThan(@Param("tenantId") String tenantId, @Param("cutoff") Instant cutoff);
}
