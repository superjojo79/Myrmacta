package com.flowforge.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowVariablesTest {

    @Test
    void withPlatformVariables_exposesDossierFieldsUnderReservedKey() {
        DossierWorkflowLink link = link();

        Map<String, Object> merged =
                WorkflowVariables.withPlatformVariables(Map.of("priority", "HIGH"), link, Map.of("montant", 42));

        assertThat(merged).containsEntry("priority", "HIGH");
        assertThat(merged).containsEntry(WorkflowVariables.DOSSIER_FIELDS, Map.of("montant", 42));
    }

    @Test
    void withPlatformVariables_defaultsDossierFieldsToEmptyMapWhenNull() {
        Map<String, Object> merged = WorkflowVariables.withPlatformVariables(null, link(), null);

        assertThat(merged).containsEntry(WorkflowVariables.DOSSIER_FIELDS, Map.of());
    }

    @Test
    void assertNoReservedKeys_rejectsAForgedConnectorResult() {
        // D3b (revue B1) : seul ConnectorServiceTask écrit connectorResult.
        assertThatThrownBy(() -> WorkflowVariables.assertNoReservedKeys(Map.of("connectorResult", Map.of("body", "{}"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("connectorResult");
    }

    @Test
    void assertNoReservedKeys_rejectsAForgedGeneratedDocument() {
        // Issue #201 : seul DocumentGenerationTask écrit generatedDocumentId.
        assertThatThrownBy(() -> WorkflowVariables.assertNoReservedKeys(Map.of("generatedDocumentId", "tenant_dev/x.pdf")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("generatedDocumentId");
        assertThat(WorkflowVariables.isReservedOrKnownKey("generatedDocumentId")).isTrue();
    }

    @Test
    void assertNoReservedKeys_rejectsDossierFieldsFromCaller() {
        assertThatThrownBy(() -> WorkflowVariables.assertNoReservedKeys(Map.of("dossierFields", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dossierFields");
    }

    private static DossierWorkflowLink link() {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tenant_dev",
                null,
                "smokeProcess",
                1,
                "STARTED",
                "manual",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null,
                "starter-sub");
    }
}
