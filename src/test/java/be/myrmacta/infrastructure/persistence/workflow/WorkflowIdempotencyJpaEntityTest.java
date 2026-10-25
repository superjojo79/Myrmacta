package be.myrmacta.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Couverture de branches manquante trouvée en clôturant fiabilisation-tests-plateforme
 * (D-COVERAGE, seuil BRANCH relevé) : {@link WorkflowIdempotencyJpaEntity.IdempotencyId#equals}
 * et {@code #hashCode} n'avaient aucun test (composite key JPA à 3 champs, {@code @IdClass}).
 */
class WorkflowIdempotencyJpaEntityTest {

    @Test
    void idempotencyId_equals_sameInstance_isTrue() {
        var id = idempotencyId("tenant_dev", UUID.randomUUID(), "key-1");

        assertThat(id.equals(id)).isTrue();
    }

    @Test
    void idempotencyId_equals_null_isFalse() {
        var id = idempotencyId("tenant_dev", UUID.randomUUID(), "key-1");

        assertThat(id.equals(null)).isFalse();
    }

    @Test
    void idempotencyId_equals_differentType_isFalse() {
        var id = idempotencyId("tenant_dev", UUID.randomUUID(), "key-1");

        assertThat(id.equals("not-an-id")).isFalse();
    }

    @Test
    void idempotencyId_equals_sameFields_isTrueAndHashCodeMatches() {
        UUID dossierId = UUID.randomUUID();
        var a = idempotencyId("tenant_dev", dossierId, "key-1");
        var b = idempotencyId("tenant_dev", dossierId, "key-1");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void idempotencyId_equals_differentTenantId_isFalse() {
        UUID dossierId = UUID.randomUUID();
        var a = idempotencyId("tenant_dev", dossierId, "key-1");
        var b = idempotencyId("tenant_other", dossierId, "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void idempotencyId_equals_differentDossierId_isFalse() {
        var a = idempotencyId("tenant_dev", UUID.randomUUID(), "key-1");
        var b = idempotencyId("tenant_dev", UUID.randomUUID(), "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void idempotencyId_equals_differentIdempotencyKey_isFalse() {
        UUID dossierId = UUID.randomUUID();
        var a = idempotencyId("tenant_dev", dossierId, "key-1");
        var b = idempotencyId("tenant_dev", dossierId, "key-2");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void entity_constructorAndAccessor() {
        UUID linkId = UUID.randomUUID();
        WorkflowIdempotencyJpaEntity entity = new WorkflowIdempotencyJpaEntity(
                "tenant_dev", UUID.randomUUID(), "key-1", linkId, Instant.parse("2026-08-22T00:00:00Z"));

        assertThat(entity.linkId()).isEqualTo(linkId);
    }

    private static WorkflowIdempotencyJpaEntity.IdempotencyId idempotencyId(
            String tenantId, UUID dossierId, String idempotencyKey) {
        try {
            var id = new WorkflowIdempotencyJpaEntity.IdempotencyId();
            Class<?> clazz = WorkflowIdempotencyJpaEntity.IdempotencyId.class;
            set(clazz, id, "tenantId", tenantId);
            set(clazz, id, "dossierId", dossierId);
            set(clazz, id, "idempotencyKey", idempotencyKey);
            return id;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Class<?> clazz, Object target, String field, Object value)
            throws ReflectiveOperationException {
        var f = clazz.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
