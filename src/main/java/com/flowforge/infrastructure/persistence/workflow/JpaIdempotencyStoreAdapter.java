package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaIdempotencyStoreAdapter implements IdempotencyStore {

    private final SpringDataWorkflowIdempotencyRepository repository;

    JpaIdempotencyStoreAdapter(SpringDataWorkflowIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findLinkId(String tenantId, UUID dossierId, String idempotencyKey) {
        return repository.findByTenantIdAndDossierIdAndIdempotencyKey(tenantId, dossierId, idempotencyKey)
                .map(WorkflowIdempotencyJpaEntity::linkId);
    }

    @Override
    @Transactional
    public void remember(String tenantId, UUID dossierId, String idempotencyKey, UUID linkId) {
        if (findLinkId(tenantId, dossierId, idempotencyKey).isPresent()) {
            return;
        }
        repository.saveAndFlush(new WorkflowIdempotencyJpaEntity(
                tenantId, dossierId, idempotencyKey, linkId, Instant.now()));
    }

    @Override
    public int purgeExpired() {
        return 0;
    }
}
