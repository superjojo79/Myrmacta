package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-02T09:00:00Z");

    @Test
    void withRetentionArchive_fromClotureSetsArchiveStatusAndPurgeEligibleAt() {
        Instant closedAt = Instant.parse("2026-08-10T00:00:00Z");
        Dossier dossier = dossier().withStatus(DossierStatus.CLOTURE, "closer", closedAt);
        Instant at = Instant.parse("2026-09-09T00:00:00Z");

        Dossier archived = dossier.withRetentionArchive("system:retention-job", at);

        assertThat(archived.status()).isEqualTo(DossierStatus.ARCHIVE);
        assertThat(archived.purgeEligibleAt()).isEqualTo(at);
        assertThat(archived.updatedBy()).isEqualTo("system:retention-job");
        assertThat(archived.updatedAt()).isEqualTo(at);
        assertThat(archived.closedBy()).isEqualTo("closer");
        assertThat(archived.closedAt()).isEqualTo(closedAt);
        // Original stays untouched — immutable copy.
        assertThat(dossier.status()).isEqualTo(DossierStatus.CLOTURE);
        assertThat(dossier.purgeEligibleAt()).isNull();
    }

    @Test
    void withRetentionArchive_rejectsTransitionWhenNotCloture() {
        Dossier dossier = dossier();

        assertThatThrownBy(() -> dossier.withRetentionArchive("system:retention-job", Instant.now()))
                .isInstanceOf(IllegalDossierStatusTransitionException.class);
    }

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

    @Test
    void toString_neverPrintsFieldValuesOrKeys() {
        // D-LOG-GUARD: toString() must stay safe unconditionally — it cannot know which
        // fields are sensitive (Dossier does not carry FieldDefinition), so it omits the
        // whole fields map rather than filtering selectively.
        Dossier dossier = dossier();

        String text = dossier.toString();

        assertThat(text).doesNotContain("montant");
        assertThat(text).doesNotContain("100");
    }

    @Test
    void toString_staysUsefulForDebugging() {
        Dossier dossier = dossier();

        String text = dossier.toString();

        assertThat(text).contains(dossier.id().toString());
        assertThat(text).contains("DOS-1");
        assertThat(text).contains("SINISTRE");
        assertThat(text).contains("fields=<1 entries redacted>");
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
                null,
                null);
    }
}
