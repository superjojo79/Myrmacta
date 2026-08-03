package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    int deleteByStatusAndProcessedAtBefore(OutboxStatus status, Instant before);
}
