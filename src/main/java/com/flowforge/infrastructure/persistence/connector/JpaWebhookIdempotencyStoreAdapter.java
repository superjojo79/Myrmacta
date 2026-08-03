package com.flowforge.infrastructure.persistence.connector;

import com.flowforge.domain.connector.port.out.WebhookIdempotencyStore;
import com.flowforge.infrastructure.config.ConnectorProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaWebhookIdempotencyStoreAdapter implements WebhookIdempotencyStore {

    private final SpringDataWebhookIdempotencyRepository repository;
    private final ConnectorProperties properties;

    JpaWebhookIdempotencyStoreAdapter(
            SpringDataWebhookIdempotencyRepository repository, ConnectorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> findResponseStatus(UUID instanceId, String idempotencyKey) {
        return repository
                .findByInstanceIdAndIdemKey(instanceId, idempotencyKey)
                .filter(row -> row.createdAt()
                        .isAfter(Instant.now().minus(properties.webhook().idempotencyTtl())))
                .map(WebhookIdempotencyJpaEntity::responseStatus);
    }

    @Override
    @Transactional
    public void store(UUID instanceId, String idempotencyKey, int responseStatus) {
        var existing = repository.findByInstanceIdAndIdemKey(instanceId, idempotencyKey);
        if (existing.isPresent()) {
            var row = existing.get();
            if (row.createdAt()
                    .isAfter(Instant.now().minus(properties.webhook().idempotencyTtl()))) {
                return;
            }
            repository.delete(row);
            repository.flush();
        }
        repository.saveAndFlush(
                new WebhookIdempotencyJpaEntity(instanceId, idempotencyKey, responseStatus, Instant.now()));
    }
}
