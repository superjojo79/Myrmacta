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
                        null))
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
                        null))
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
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedDossierStatuses");
    }

    @Test
    void unknownStatusRejectedByValidator() {
        WorkflowTypeConfig config = validConfig();
        assertThat(config.isKnownStatus("NOPE")).isFalse();
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
                DossierStatus.EN_COURS);
    }
}
