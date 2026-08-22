package com.flowforge.infrastructure.persistence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Couverture de branches manquante trouvée en clôturant fiabilisation-tests-plateforme
 * (D-COVERAGE, seuil BRANCH relevé) : {@link WebhookIdempotencyJpaEntity.Pk#equals} et
 * {@code #hashCode} n'avaient aucun test (composite key JPA, {@code @IdClass}).
 */
class WebhookIdempotencyJpaEntityTest {

    @Test
    void pk_equals_sameInstance_isTrue() {
        WebhookIdempotencyJpaEntity.Pk pk = pk(UUID.randomUUID(), "key-1");

        assertThat(pk.equals(pk)).isTrue();
    }

    @Test
    void pk_equals_null_isFalse() {
        WebhookIdempotencyJpaEntity.Pk pk = pk(UUID.randomUUID(), "key-1");

        assertThat(pk.equals(null)).isFalse();
    }

    @Test
    void pk_equals_differentType_isFalse() {
        WebhookIdempotencyJpaEntity.Pk pk = pk(UUID.randomUUID(), "key-1");

        assertThat(pk.equals("not-a-pk")).isFalse();
    }

    @Test
    void pk_equals_sameFields_isTrueAndHashCodeMatches() {
        UUID instanceId = UUID.randomUUID();
        WebhookIdempotencyJpaEntity.Pk a = pk(instanceId, "key-1");
        WebhookIdempotencyJpaEntity.Pk b = pk(instanceId, "key-1");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void pk_equals_differentInstanceId_isFalse() {
        WebhookIdempotencyJpaEntity.Pk a = pk(UUID.randomUUID(), "key-1");
        WebhookIdempotencyJpaEntity.Pk b = pk(UUID.randomUUID(), "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void pk_equals_differentIdemKey_isFalse() {
        UUID instanceId = UUID.randomUUID();
        WebhookIdempotencyJpaEntity.Pk a = pk(instanceId, "key-1");
        WebhookIdempotencyJpaEntity.Pk b = pk(instanceId, "key-2");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void entity_constructorAndAccessors() {
        UUID instanceId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.parse("2026-08-22T00:00:00Z");
        WebhookIdempotencyJpaEntity entity = new WebhookIdempotencyJpaEntity(instanceId, "key-1", 200, now);

        assertThat(entity.responseStatus()).isEqualTo(200);
        assertThat(entity.createdAt()).isEqualTo(now);

        entity.responseStatus(201);
        assertThat(entity.responseStatus()).isEqualTo(201);
    }

    private static WebhookIdempotencyJpaEntity.Pk pk(UUID instanceId, String idemKey) {
        try {
            WebhookIdempotencyJpaEntity.Pk pk = new WebhookIdempotencyJpaEntity.Pk();
            var instanceIdField = WebhookIdempotencyJpaEntity.Pk.class.getDeclaredField("instanceId");
            instanceIdField.setAccessible(true);
            instanceIdField.set(pk, instanceId);
            var idemKeyField = WebhookIdempotencyJpaEntity.Pk.class.getDeclaredField("idemKey");
            idemKeyField.setAccessible(true);
            idemKeyField.set(pk, idemKey);
            return pk;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
