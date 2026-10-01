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

interface SpringDataAssistantUsageRepository extends JpaRepository<AssistantUsageJpaEntity, AssistantUsageJpaEntity.Key> {

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AssistantUsageJpaEntity u set u.tokens = u.tokens + :tokens "
            + "where u.key.tenantId = :tenantId and u.key.day = :day")
    int add(@Param("tenantId") String tenantId, @Param("day") LocalDate day, @Param("tokens") long tokens);
}
