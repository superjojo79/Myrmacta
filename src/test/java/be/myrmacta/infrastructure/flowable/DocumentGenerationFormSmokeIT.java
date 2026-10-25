package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.in.GenerateDocumentUseCase;
import be.myrmacta.domain.document.port.in.GenerateDocumentUseCase.GenerateDocumentCommand;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regression fixture ({@code document-generation-form-smoke.bpmn20.xml}) for a real product bug
 * found during Phase 9 adversarial review of TODO §52 (formulaires-dynamiques-taches): the
 * {@code document-generation} task form emitted completion variables as
 * {@code templateKey}/{@code variantKey}/{@code locale} -- the {@link
 * be.myrmacta.infrastructure.web.dossier.DossierApiDtos.AllowedDocumentTemplateResponse} DTO's
 * own field names -- instead of {@code documentType}/{@code documentVariant}/{@code
 * documentLocale}, the names {@link DocumentGenerationTask} actually reads via {@code
 * execution.getVariable(...)}. Every test covering that mismatch (DocumentGenerationTaskTest,
 * DocumentGenerationTaskForm.spec.ts before this fix, E2E-FORM-2 before this fix) exercised only
 * one side of the boundary in isolation and could not have caught it. This test drives the real
 * Flowable 8.0.0 engine across both tasks -- {@code taskService.complete(taskId, variables)} on
 * the {@code userTask}, then the {@code serviceTask ${documentGenerationTask}} it triggers -- so a
 * future change to either side's variable names fails here even if the unit-level tests on both
 * sides are individually kept in sync with a shared (but wrong) name.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DocumentGenerationFormSmokeIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TenantProperties tenantProperties;

    @MockitoBean
    private GenerateDocumentUseCase generateDocumentUseCase;

    @MockitoBean
    private DocumentStoragePort documentStoragePort;

    @Test
    void completingDocumentGenerationTaskForm_feedsServiceTaskWithMatchingVariableNames() {
        when(generateDocumentUseCase.generate(any())).thenReturn("%PDF".getBytes());
        when(documentStoragePort.store(eq(tenantProperties.id()), eq("application/pdf"), any()))
                .thenReturn("tenant_dev/generated.pdf");

        String tenantId = tenantProperties.id();
        runtimeService.startProcessInstanceByKeyAndTenantId(
                "documentGenerationFormSmoke", "doc-gen-form-smoke-" + UUID.randomUUID(), tenantId);

        Task task = taskService.createTaskQuery()
                .processDefinitionKey("documentGenerationFormSmoke")
                .active()
                .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getFormKey()).isEqualTo("document-generation");

        // Exactly what DocumentGenerationTaskForm.vue's `ready` payload becomes on the wire via
        // POST /api/workflow-tasks/{taskId}/complete.
        taskService.complete(
                task.getId(),
                Map.of(
                        "documentType", "invoice",
                        "documentVariant", "premium",
                        "documentLocale", "fr"));

        verify(generateDocumentUseCase).generate(new GenerateDocumentCommand(
                "invoice", "premium", "fr", Map.of(
                        "documentType", "invoice",
                        "documentVariant", "premium",
                        "documentLocale", "fr")));

        assertThat(taskService.createTaskQuery()
                        .processDefinitionKey("documentGenerationFormSmoke")
                        .active()
                        .list())
                .isEmpty();
    }
}
