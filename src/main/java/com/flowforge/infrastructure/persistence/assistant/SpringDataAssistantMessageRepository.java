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

interface SpringDataAssistantMessageRepository extends JpaRepository<AssistantMessageJpaEntity, UUID> {

    List<AssistantMessageJpaEntity> findByConversationIdOrderBySeqAsc(UUID conversationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistantMessageJpaEntity m where m.conversationId = :id")
    int deleteByConversation(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistantMessageJpaEntity m where m.conversationId in "
            + "(select c.id from AssistantConversationJpaEntity c where c.tenantId = :tenantId and c.updatedAt < :cutoff)")
    int deleteOlderThan(@Param("tenantId") String tenantId, @Param("cutoff") Instant cutoff);
}
