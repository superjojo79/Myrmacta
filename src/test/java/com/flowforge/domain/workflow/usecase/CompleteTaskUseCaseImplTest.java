package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompleteTaskUseCaseImplTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final UUID DOSSIER_ID = UUID.randomUUID();

    private WorkflowEnginePort workflowEnginePort;
    private DossierRepository dossierRepository;
    private DossierTypeRepository dossierTypeRepository;
    private CompleteTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        workflowEnginePort = mock(WorkflowEnginePort.class);
        dossierRepository = mock(DossierRepository.class);
        dossierTypeRepository = mock(DossierTypeRepository.class);
        useCase = new CompleteTaskUseCaseImpl(TENANT_ID, workflowEnginePort, dossierRepository, dossierTypeRepository);
    }

    @Test
    void complete_forwardsVariablesToEnginePort() {
        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"));

        verify(workflowEnginePort).completeTask("task-1", Map.of("decision", "OK"));
    }

    @Test
    void complete_rejectsReservedVariableKeys() {
        assertThatThrownBy(() -> useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("dossierId", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verifyNoInteractions(workflowEnginePort);
    }

    @Test
    void complete_withoutDocumentTypeVariable_neverConsultsDossierOrTypeRepositories() {
        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"));

        verifyNoInteractions(dossierRepository, dossierTypeRepository);
    }

    @Test
    void complete_withDocumentTypeAmongTheDossierTypesAllowedKeys_succeeds() {
        when(dossierRepository.findById(DOSSIER_ID)).thenReturn(Optional.of(dossier("CLAIM")));
        when(dossierTypeRepository.findByCode(TENANT_ID, "CLAIM"))
                .thenReturn(Optional.of(dossierType("CLAIM", List.of("invoice", "receipt"))));

        useCase.complete(
                "task-1",
                DOSSIER_ID.toString(),
                Map.of(WorkflowVariables.DOCUMENT_TYPE, "invoice"));

        verify(workflowEnginePort).completeTask("task-1", Map.of(WorkflowVariables.DOCUMENT_TYPE, "invoice"));
    }

    @Test
    void complete_withDocumentTypeNotAmongTheDossierTypesAllowedKeys_rejectsAndNeverReachesTheEngine() {
        // Phase 9 adversarial review finding E4: without this check, any actor able to complete a
        // task (EDIT on the dossier) could smuggle an arbitrary documentType into the
        // ${documentGenerationTask} serviceTask that follows, regardless of the
        // DossierType.documentTemplateKeys() the tenant admin actually configured -- defeating the
        // whole point of "Templates de documents autorisés" as anything more than a UI suggestion.
        when(dossierRepository.findById(DOSSIER_ID)).thenReturn(Optional.of(dossier("CLAIM")));
        when(dossierTypeRepository.findByCode(TENANT_ID, "CLAIM"))
                .thenReturn(Optional.of(dossierType("CLAIM", List.of("invoice"))));

        assertThatThrownBy(() -> useCase.complete(
                        "task-1",
                        DOSSIER_ID.toString(),
                        Map.of(WorkflowVariables.DOCUMENT_TYPE, "some-other-tenants-secret-template")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("some-other-tenants-secret-template");
        verifyNoInteractions(workflowEnginePort);
    }

    @Test
    void complete_withDocumentTypeButNoAllowedKeysConfiguredAtAll_rejects() {
        when(dossierRepository.findById(DOSSIER_ID)).thenReturn(Optional.of(dossier("CLAIM")));
        when(dossierTypeRepository.findByCode(TENANT_ID, "CLAIM"))
                .thenReturn(Optional.of(dossierType("CLAIM", List.of())));

        assertThatThrownBy(() -> useCase.complete(
                        "task-1", DOSSIER_ID.toString(), Map.of(WorkflowVariables.DOCUMENT_TYPE, "invoice")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(workflowEnginePort);
    }

    private static Dossier dossier(String dossierTypeCode) {
        return new Dossier(
                DOSSIER_ID,
                TENANT_ID,
                "DOS-1",
                dossierTypeCode,
                "Label",
                DossierStatus.EN_COURS,
                Map.of(),
                null,
                1,
                "u",
                Instant.now(),
                "u",
                Instant.now(),
                null,
                null,
                null);
    }

    private static DossierType dossierType(String code, List<String> documentTemplateKeys) {
        return new DossierType(
                UUID.randomUUID(),
                TENANT_ID,
                code,
                "Label",
                "wf-key",
                true,
                false,
                List.of(),
                null,
                false,
                null,
                documentTemplateKeys,
                "u",
                "u");
    }
}
