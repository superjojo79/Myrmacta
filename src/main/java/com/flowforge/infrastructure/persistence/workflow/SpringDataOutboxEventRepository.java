package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    int deleteByStatusAndProcessedAtBefore(OutboxStatus status, Instant before);

    @Query("select min(e.createdAt) from OutboxEventJpaEntity e where e.status = :status")
    Optional<Instant> findOldestCreatedAtByStatus(@Param("status") OutboxStatus status);
}
