package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.workflow.TaskQuestionFormKeys;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.model.TaskQuestionAnswer;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompleteTaskUseCaseImplTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final UUID DOSSIER_ID = UUID.randomUUID();
    private static final String ACTOR = "actor-1";
    private static final AccessContext.User ACTOR_USER = AccessContext.user(ACTOR, Set.of("DOSSIER_MANAGER"));

    private WorkflowEnginePort workflowEnginePort;
    private DossierRepository dossierRepository;
    private DossierTypeRepository dossierTypeRepository;
    private TaskQuestionSchemaRepository taskQuestionSchemaRepository;
    private TaskQuestionAnswerRepository taskQuestionAnswerRepository;
    private CompleteTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        workflowEnginePort = mock(WorkflowEnginePort.class);
        dossierRepository = mock(DossierRepository.class);
        dossierTypeRepository = mock(DossierTypeRepository.class);
        taskQuestionSchemaRepository = mock(TaskQuestionSchemaRepository.class);
        taskQuestionAnswerRepository = mock(TaskQuestionAnswerRepository.class);
        useCase = new CompleteTaskUseCaseImpl(
                TENANT_ID,
                workflowEnginePort,
                dossierRepository,
                dossierTypeRepository,
                taskQuestionSchemaRepository,
                taskQuestionAnswerRepository);
    }

    @Test
    void complete_forwardsVariablesToEnginePort() {
        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"), ACTOR_USER);

        verify(workflowEnginePort).completeTask("task-1", Map.of("decision", "OK"));
    }

    @Test
    void complete_rejectsReservedVariableKeys() {
        assertThatThrownBy(() -> useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("dossierId", "x"), ACTOR_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(workflowEnginePort, never()).completeTask(any(), any());
    }

    @Test
    void complete_withoutDocumentTypeVariable_neverConsultsDossierOrTypeRepositories() {
        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"), ACTOR_USER);

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
                Map.of(WorkflowVariables.DOCUMENT_TYPE, "invoice"),
                ACTOR_USER);

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
                        Map.of(WorkflowVariables.DOCUMENT_TYPE, "some-other-tenants-secret-template"),
                        ACTOR_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("some-other-tenants-secret-template");
        verify(workflowEnginePort, never()).completeTask(any(), any());
    }

    @Test
    void complete_withDocumentTypeButNoAllowedKeysConfiguredAtAll_rejects() {
        when(dossierRepository.findById(DOSSIER_ID)).thenReturn(Optional.of(dossier("CLAIM")));
        when(dossierTypeRepository.findByCode(TENANT_ID, "CLAIM"))
                .thenReturn(Optional.of(dossierType("CLAIM", List.of())));

        assertThatThrownBy(() -> useCase.complete(
                        "task-1", DOSSIER_ID.toString(), Map.of(WorkflowVariables.DOCUMENT_TYPE, "invoice"), ACTOR_USER))
                .isInstanceOf(IllegalArgumentException.class);
        verify(workflowEnginePort, never()).completeTask(any(), any());
    }

    @Test
    void complete_withoutUserQuestionFormKey_neverConsultsSchemaOrAnswerRepositories() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1")).thenReturn(Optional.of("dossier-fields"));

        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"), ACTOR_USER);

        verifyNoInteractions(taskQuestionSchemaRepository, taskQuestionAnswerRepository);
    }

    @Test
    void complete_withUserQuestionFormKey_validatesAndRecordsAnswer_thenCompletesTask() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        when(workflowEnginePort.findProcessInstanceIdByTaskId("task-1")).thenReturn(Optional.of("proc-1"));
        when(taskQuestionSchemaRepository.findByKey(TENANT_ID, "orientation-dossier"))
                .thenReturn(Optional.of(questionSchema()));

        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "approve"), ACTOR_USER);

        ArgumentCaptor<TaskQuestionAnswer> captor = ArgumentCaptor.forClass(TaskQuestionAnswer.class);
        verify(taskQuestionAnswerRepository).save(captor.capture());
        TaskQuestionAnswer saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.dossierId()).isEqualTo(DOSSIER_ID);
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.processInstanceId()).isEqualTo("proc-1");
        assertThat(saved.schemaKey()).isEqualTo("orientation-dossier");
        assertThat(saved.answers()).isEqualTo(Map.of("decision", "approve"));
        assertThat(saved.submittedBy()).isEqualTo(ACTOR);
        verify(workflowEnginePort).completeTask("task-1", Map.of("decision", "approve"));
    }

    @Test
    void complete_withUserQuestionFormKey_missingRequiredField_rejectsAndNeverReachesTheEngine() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        when(taskQuestionSchemaRepository.findByKey(TENANT_ID, "orientation-dossier"))
                .thenReturn(Optional.of(questionSchema()));

        assertThatThrownBy(() -> useCase.complete("task-1", DOSSIER_ID.toString(), Map.of(), ACTOR_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision");
        verify(workflowEnginePort, org.mockito.Mockito.never()).completeTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(taskQuestionAnswerRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void complete_withUserQuestionFormKey_valueNotInSelectOptions_rejects() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        when(taskQuestionSchemaRepository.findByKey(TENANT_ID, "orientation-dossier"))
                .thenReturn(Optional.of(questionSchema()));

        assertThatThrownBy(() -> useCase.complete(
                        "task-1", DOSSIER_ID.toString(), Map.of("decision", "maybe"), ACTOR_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_OPTION");
        verify(taskQuestionAnswerRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void complete_withUserQuestionFormKey_wrongTypeForNumberField_rejects() {
        // Phase 7 review finding: the original hand-rolled validation only checked required/SELECT
        // options, silently accepting a non-numeric value on a NUMBER field. Replaced with
        // DossierFieldValidator (shared with dossier fields), which also enforces type conformance.
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        TaskQuestionSchema schemaWithNumberField = new TaskQuestionSchema(
                UUID.randomUUID(),
                TENANT_ID,
                "orientation-dossier",
                "Orientation",
                List.of(new FieldDefinition("amount", "Amount", FieldType.NUMBER, true, null, List.of(), null)),
                "u",
                "u");
        when(taskQuestionSchemaRepository.findByKey(TENANT_ID, "orientation-dossier"))
                .thenReturn(Optional.of(schemaWithNumberField));

        assertThatThrownBy(() -> useCase.complete(
                        "task-1", DOSSIER_ID.toString(), Map.of("amount", "not-a-number"), ACTOR_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TYPE_MISMATCH");
        verify(taskQuestionAnswerRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void complete_withUserQuestionFormKey_variableNotDeclaredByTheSchema_rejectsAndNeverReachesTheEngine() {
        // Adversarial review finding (post-Phase-9): allowUnknownFields was hard-coded true and the
        // raw `variables` (not the schema-filtered `answers`) reached completeTask -- so a task
        // holder could smuggle an extra process variable (e.g. one read by a downstream exclusive
        // gateway) past a schema whose entire purpose is to bound what this task can produce, and
        // it would never even appear in the TaskQuestionAnswer trace. Now rejected outright.
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        when(taskQuestionSchemaRepository.findByKey(TENANT_ID, "orientation-dossier"))
                .thenReturn(Optional.of(questionSchema()));

        assertThatThrownBy(() -> useCase.complete(
                        "task-1",
                        DOSSIER_ID.toString(),
                        Map.of("decision", "approve", "priority", "URGENT"),
                        ACTOR_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_FIELD");
        verify(workflowEnginePort, org.mockito.Mockito.never()).completeTask(any(), any());
        verify(taskQuestionAnswerRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void complete_rejectsWhenActorIsNotTheAssignee() {
        when(workflowEnginePort.findAssigneeByTaskId("task-1")).thenReturn(Optional.of("other-user"));

        assertThatThrownBy(() -> useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"), ACTOR_USER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("assignee");
        verify(workflowEnginePort, never()).completeTask(any(), any());
    }

    @Test
    void complete_allowsAdminToCompleteAnotherUsersTask() {
        when(workflowEnginePort.findAssigneeByTaskId("task-1")).thenReturn(Optional.of("other-user"));
        AccessContext.User admin = AccessContext.user("admin-1", Set.of("WORKFLOW_ADMIN"));

        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"), admin);

        verify(workflowEnginePort).completeTask("task-1", Map.of("decision", "OK"));
    }

    @Test
    void complete_allowsAssigneeToComplete() {
        when(workflowEnginePort.findAssigneeByTaskId("task-1")).thenReturn(Optional.of(ACTOR));

        useCase.complete("task-1", DOSSIER_ID.toString(), Map.of("decision", "OK"), ACTOR_USER);

        verify(workflowEnginePort).completeTask("task-1", Map.of("decision", "OK"));
    }

    @Test
    void complete_withUserQuestionFormKey_unknownSchema_rejects() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of("user-question:missing-schema"));
        when(taskQuestionSchemaRepository.findByKey(TENANT_ID, "missing-schema")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.complete("task-1", DOSSIER_ID.toString(), Map.of(), ACTOR_USER))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }

    private static TaskQuestionSchema questionSchema() {
        return new TaskQuestionSchema(
                UUID.randomUUID(),
                TENANT_ID,
                "orientation-dossier",
                "Orientation",
                List.of(new FieldDefinition("decision", "Decision", FieldType.SELECT, true, null,
                        List.of("approve", "reject"), null)),
                "u",
                "u");
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
