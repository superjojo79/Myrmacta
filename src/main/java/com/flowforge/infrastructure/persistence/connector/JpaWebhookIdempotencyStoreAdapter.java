package com.flowforge.infrastructure.persistence.connector;

import com.flowforge.domain.connector.model.WebhookIdempotencyInFlightException;
import com.flowforge.domain.connector.port.out.WebhookIdempotencyStore;
import com.flowforge.infrastructure.config.ConnectorProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic claim via INSERT of in-flight status {@code 0}; {@link #complete} writes the final HTTP
 * status. Concurrent claims hit the PK and re-read (D-IDEM).
 */
@Component
class JpaWebhookIdempotencyStoreAdapter implements WebhookIdempotencyStore {

    /** Marker status while the webhook handler is still processing. */
    static final int IN_FLIGHT = 0;

    private final SpringDataWebhookIdempotencyRepository repository;
    private final ConnectorProperties properties;

    JpaWebhookIdempotencyStoreAdapter(
            SpringDataWebhookIdempotencyRepository repository, ConnectorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public Optional<Integer> beginOrGetCompleted(UUID instanceId, String idempotencyKey) {
        Optional<WebhookIdempotencyJpaEntity> existing =
                repository.findByInstanceIdAndIdemKey(instanceId, idempotencyKey);
        if (existing.isPresent()) {
            return interpretExisting(instanceId, idempotencyKey, existing.get());
        }
        try {
            repository.saveAndFlush(
                    new WebhookIdempotencyJpaEntity(instanceId, idempotencyKey, IN_FLIGHT, Instant.now()));
            return Optional.empty();
        } catch (DataIntegrityViolationException ex) {
            WebhookIdempotencyJpaEntity raced = repository
                    .findByInstanceIdAndIdemKey(instanceId, idempotencyKey)
                    .orElseThrow(() -> ex);
            return interpretExisting(instanceId, idempotencyKey, raced);
        }
    }

    @Override
    @Transactional
    public void complete(UUID instanceId, String idempotencyKey, int responseStatus) {
        WebhookIdempotencyJpaEntity row = repository
                .findByInstanceIdAndIdemKey(instanceId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key was not claimed before complete: " + idempotencyKey));
        row.responseStatus(responseStatus);
        repository.saveAndFlush(row);
    }

    @Override
    @Transactional
    public void release(UUID instanceId, String idempotencyKey) {
        repository.findByInstanceIdAndIdemKey(instanceId, idempotencyKey)
                .filter(row -> row.responseStatus() == IN_FLIGHT)
                .ifPresent(row -> {
                    repository.delete(row);
                    repository.flush();
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> findResponseStatus(UUID instanceId, String idempotencyKey) {
        return repository
                .findByInstanceIdAndIdemKey(instanceId, idempotencyKey)
                .filter(row -> row.createdAt()
                        .isAfter(Instant.now().minus(properties.webhook().idempotencyTtl())))
                .filter(row -> row.responseStatus() != IN_FLIGHT)
                .map(WebhookIdempotencyJpaEntity::responseStatus);
    }

    private Optional<Integer> interpretExisting(
            UUID instanceId, String idempotencyKey, WebhookIdempotencyJpaEntity row) {
        if (row.createdAt().isBefore(Instant.now().minus(properties.webhook().idempotencyTtl()))) {
            repository.delete(row);
            repository.flush();
            try {
                repository.saveAndFlush(
                        new WebhookIdempotencyJpaEntity(instanceId, idempotencyKey, IN_FLIGHT, Instant.now()));
                return Optional.empty();
            } catch (DataIntegrityViolationException ex) {
                WebhookIdempotencyJpaEntity raced = repository
                        .findByInstanceIdAndIdemKey(instanceId, idempotencyKey)
                        .orElseThrow(() -> ex);
                return interpretExisting(instanceId, idempotencyKey, raced);
            }
        }
        if (row.responseStatus() == IN_FLIGHT) {
            throw new WebhookIdempotencyInFlightException(instanceId, idempotencyKey);
        }
        return Optional.of(row.responseStatus());
    }
}
