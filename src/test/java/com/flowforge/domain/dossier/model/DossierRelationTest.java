package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierRelationTest {

    private static final Instant T0 = Instant.parse("2026-08-15T08:00:00Z");

    @Test
    void rejectsAutoRelation() {
        UUID same = UUID.randomUUID();

        assertThatThrownBy(() -> relation(same, same, "tenant_dev"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source")
                .hasMessageContaining("target");
    }

    @Test
    void acceptsDistinctDossiersWithNullableIdempotencyKey() {
        DossierRelation relation = relation(UUID.randomUUID(), UUID.randomUUID(), "tenant_dev");

        assertThat(relation.type()).isEqualTo(RelationType.PARENT_CHILD);
        assertThat(relation.idempotencyKey()).isNull();
        assertThat(relation.tenantId()).isEqualTo("tenant_dev");
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new DossierRelation(
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "tenant_dev",
                        RelationType.LINKED,
                        "u1",
                        T0,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    private static DossierRelation relation(UUID source, UUID target, String tenantId) {
        return new DossierRelation(
                UUID.randomUUID(),
                source,
                target,
                tenantId,
                RelationType.PARENT_CHILD,
                "u1",
                T0,
                null);
    }
}
