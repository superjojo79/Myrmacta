package com.flowforge.infrastructure.persistence.workflow;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkflowIdempotencyRepository
        extends JpaRepository<WorkflowIdempotencyJpaEntity, WorkflowIdempotencyJpaEntity.IdempotencyId> {

    Optional<WorkflowIdempotencyJpaEntity> findByTenantIdAndDossierIdAndIdempotencyKey(
            String tenantId, UUID dossierId, String idempotencyKey);
}
