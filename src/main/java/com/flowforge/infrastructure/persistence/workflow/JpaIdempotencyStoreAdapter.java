package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.infrastructure.config.WorkflowProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaIdempotencyStoreAdapter implements IdempotencyStore {

    private final SpringDataWorkflowIdempotencyRepository repository;
    private final WorkflowProperties properties;

    JpaIdempotencyStoreAdapter(SpringDataWorkflowIdempotencyRepository repository, WorkflowProperties properties) {
        this.repository = repository;
        this.properties = properties;
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
    @Transactional
    public int purgeExpired() {
        return repository.deleteByCreatedAtBefore(Instant.now().minus(properties.idempotencyRetention()));
    }
}
