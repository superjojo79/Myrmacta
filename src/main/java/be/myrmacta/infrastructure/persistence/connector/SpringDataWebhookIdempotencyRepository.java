package be.myrmacta.infrastructure.persistence.connector;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWebhookIdempotencyRepository
        extends JpaRepository<WebhookIdempotencyJpaEntity, WebhookIdempotencyJpaEntity.Pk> {

    Optional<WebhookIdempotencyJpaEntity> findByInstanceIdAndIdemKey(UUID instanceId, String idemKey);
}
