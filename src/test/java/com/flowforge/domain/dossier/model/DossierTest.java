package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-02T09:00:00Z");

    @Test
    void withFields_threeArg_setsFieldsUpdatedByAndUpdatedAt() {
        Dossier dossier = dossier();
        Instant at = Instant.parse("2026-08-15T10:00:00Z");

        Dossier updated = dossier.withFields(Map.of("montant", 200), "editor", at);

        assertThat(updated.fields()).containsEntry("montant", 200);
        assertThat(updated.updatedBy()).isEqualTo("editor");
        assertThat(updated.updatedAt()).isEqualTo(at);
    }

    @Test
    void withFields_threeArg_leavesStatusVersionAndClosureUnchanged() {
        Dossier dossier = dossier().withStatus(DossierStatus.CLOTURE, "closer", Instant.parse("2026-08-10T00:00:00Z"))
                .withVersion(5);

        Dossier updated = dossier.withFields(Map.of("montant", 200), "editor", Instant.parse("2026-08-15T10:00:00Z"));

        assertThat(updated.status()).isEqualTo(DossierStatus.CLOTURE);
        assertThat(updated.version()).isEqualTo(5);
        assertThat(updated.closedBy()).isEqualTo("closer");
        assertThat(updated.closedAt()).isEqualTo(Instant.parse("2026-08-10T00:00:00Z"));
    }

    @Test
    void withFields_singleArg_stillLeavesUpdatedByAndUpdatedAtUnchanged() {
        // Existing overload, used by DossierFieldRedactor for read-side redaction — must keep
        // its original no-touch behavior for updatedBy/updatedAt.
        Dossier dossier = dossier();

        Dossier updated = dossier.withFields(Map.of("montant", 200));

        assertThat(updated.fields()).containsEntry("montant", 200);
        assertThat(updated.updatedBy()).isEqualTo(dossier.updatedBy());
        assertThat(updated.updatedAt()).isEqualTo(dossier.updatedAt());
    }

    private static Dossier dossier() {
        return new Dossier(
                UUID.randomUUID(),
                "tenant_dev",
                "DOS-1",
                "SINISTRE",
                "Dossier",
                DossierStatus.OUVERT,
                Map.of("montant", 100),
                null,
                0,
                "creator",
                CREATED_AT,
                "creator",
                CREATED_AT,
                null,
                null);
    }
}
