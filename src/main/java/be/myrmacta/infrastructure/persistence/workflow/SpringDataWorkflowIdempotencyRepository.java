package be.myrmacta.infrastructure.persistence.workflow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataWorkflowIdempotencyRepository
        extends JpaRepository<WorkflowIdempotencyJpaEntity, WorkflowIdempotencyJpaEntity.IdempotencyId> {

    Optional<WorkflowIdempotencyJpaEntity> findByTenantIdAndDossierIdAndIdempotencyKey(
            String tenantId, UUID dossierId, String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkflowIdempotencyJpaEntity e where e.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") Instant threshold);
}
