package com.flowforge.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.DossierStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowTypeConfigTest {

    @Test
    void validatesInitialStatusAndAllowedDossierStatuses() {
        WorkflowTypeConfig config = validConfig();

        assertThat(config.initialStatusCode()).isEqualTo("INIT");
        assertThat(config.allowsDossierStatus(DossierStatus.OUVERT)).isTrue();
        assertThat(config.allowsDossierStatus(DossierStatus.DRAFT)).isFalse();
        assertThat(config.isKnownStatus("DONE")).isTrue();
        assertThat(config.outcomeOf("DONE")).isEqualTo(StatusOutcome.COMPLETED);
    }

    @Test
    void rejectsMissingInitialStatus() {
        assertThatThrownBy(() -> new WorkflowTypeConfig(
                        UUID.randomUUID(),
                        "tenant_a",
                        "smokeProcess",
                        "Smoke",
                        List.of(new WorkflowStatusDefinition("DONE", "Done", false, StatusOutcome.COMPLETED)),
                        List.of(DossierStatus.OUVERT),
                        null,
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initial");
    }

    @Test
    void rejectsMultipleInitialStatuses() {
        assertThatThrownBy(() -> new WorkflowTypeConfig(
                        UUID.randomUUID(),
                        "tenant_a",
                        "smokeProcess",
                        "Smoke",
                        List.of(
                                new WorkflowStatusDefinition("INIT", "Init", true, StatusOutcome.NONE),
                                new WorkflowStatusDefinition("ALSO", "Also", true, StatusOutcome.NONE)),
                        List.of(DossierStatus.OUVERT),
                        null,
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one initial");
    }

    @Test
    void rejectsEmptyAllowedDossierStatuses() {
        assertThatThrownBy(() -> new WorkflowTypeConfig(
                        UUID.randomUUID(),
                        "tenant_a",
                        "smokeProcess",
                        "Smoke",
                        List.of(new WorkflowStatusDefinition("INIT", "Init", true, StatusOutcome.NONE)),
                        List.of(),
                        null,
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedDossierStatuses");
    }

    @Test
    void unknownStatusRejectedByValidator() {
        WorkflowTypeConfig config = validConfig();
        assertThat(config.isKnownStatus("NOPE")).isFalse();
    }

    @Test
    void observerUserIds_defaultsToEmptyImmutableList() {
        WorkflowTypeConfig config = validConfig();

        assertThat(config.observerUserIds()).isEmpty();
        assertThatThrownBy(() -> config.observerUserIds().add("u1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void observerUserIds_nullTreatedAsEmpty() {
        WorkflowTypeConfig config = new WorkflowTypeConfig(
                UUID.randomUUID(),
                "tenant_a",
                "smokeProcess",
                "Smoke",
                List.of(new WorkflowStatusDefinition("INIT", "Init", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT),
                null,
                null);

        assertThat(config.observerUserIds()).isEmpty();
    }

    @Test
    void observerUserIds_rejectsBlankEntries() {
        assertThatThrownBy(() -> new WorkflowTypeConfig(
                        UUID.randomUUID(),
                        "tenant_a",
                        "smokeProcess",
                        "Smoke",
                        List.of(new WorkflowStatusDefinition("INIT", "Init", true, StatusOutcome.NONE)),
                        List.of(DossierStatus.OUVERT),
                        null,
                        List.of("user-a", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observerUserIds");
    }

    @Test
    void observerUserIds_deduplicatesPreservingOrder() {
        WorkflowTypeConfig config = new WorkflowTypeConfig(
                UUID.randomUUID(),
                "tenant_a",
                "smokeProcess",
                "Smoke",
                List.of(new WorkflowStatusDefinition("INIT", "Init", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT),
                null,
                List.of("user-a", "user-b", "user-a"));

        assertThat(config.observerUserIds()).containsExactly("user-a", "user-b");
    }

    private static WorkflowTypeConfig validConfig() {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                "tenant_a",
                "smokeProcess",
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("INIT", "Init", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("DONE", "Done", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS, DossierStatus.SUSPENDU),
                DossierStatus.EN_COURS,
                List.of());
    }
}
