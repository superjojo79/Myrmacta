package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.workflow.TaskQuestionFormKeys;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.model.UnknownTaskQuestionSchemaException;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTaskQuestionSchemaForTaskUseCaseImplTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final AccessContext.User ACTOR = AccessContext.user("actor-1", Set.of("DOSSIER_MANAGER"));

    private TaskQuestionSchemaRepository schemaRepository;
    private WorkflowEnginePort workflowEnginePort;
    private GetTaskQuestionSchemaForTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        schemaRepository = mock(TaskQuestionSchemaRepository.class);
        workflowEnginePort = mock(WorkflowEnginePort.class);
        useCase = new GetTaskQuestionSchemaForTaskUseCaseImpl(TENANT_ID, schemaRepository, workflowEnginePort);
    }

    @Test
    void getForTask_returnsSchema_whenTaskFormKeyReferencesIt() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        TaskQuestionSchema schema = schema("orientation-dossier");
        when(schemaRepository.findByKey(TENANT_ID, "orientation-dossier")).thenReturn(Optional.of(schema));

        TaskQuestionSchema result = useCase.getForTask("task-1", "orientation-dossier", ACTOR);

        assertThat(result).isEqualTo(schema);
    }

    @Test
    void getForTask_rejects_whenTaskFormKeyReferencesADifferentSchema() {
        // D-USERQ-RUNTIME-SCOPE: a task holder must not be able to read an arbitrary schemaKey by
        // guessing it -- only the schema actually referenced by their own task's formKey.
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of("user-question:other-schema"));

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier", ACTOR))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }

    @Test
    void getForTask_rejects_whenTaskHasNoFormKey() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier", ACTOR))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }

    @Test
    void getForTask_rejects_whenTaskFormKeyIsADifferentKind() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1")).thenReturn(Optional.of("document-generation"));

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier", ACTOR))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }

    @Test
    void getForTask_rejects_whenFormKeyMatchesButSchemaWasDeleted() {
        // Phase 8 coverage review: the formKey-match branch was covered, but not the case where it
        // matches and the schema itself is nonetheless gone (deleted after the task was created,
        // D-USERQ-SCHEMA-VERSIONING -- degrades the same way as an unknown formKey).
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        when(schemaRepository.findByKey(TENANT_ID, "orientation-dossier")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier", ACTOR))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }

    @Test
    void getForTask_rejectsWhenActorIsNotTheAssignee() {
        when(workflowEnginePort.findAssigneeByTaskId("task-1")).thenReturn(Optional.of("other-user"));

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier", ACTOR))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("assignee");
    }

    @Test
    void getForTask_rejectsNonAssigneeBeforeFormKeyMismatch() {
        when(workflowEnginePort.findAssigneeByTaskId("task-1")).thenReturn(Optional.of("other-user"));
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of("user-question:other-schema"));

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier", ACTOR))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getForTask_allowsAdminToReadAnotherUsersTaskSchema() {
        when(workflowEnginePort.findAssigneeByTaskId("task-1")).thenReturn(Optional.of("other-user"));
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of(TaskQuestionFormKeys.formKeyFor("orientation-dossier")));
        TaskQuestionSchema schema = schema("orientation-dossier");
        when(schemaRepository.findByKey(TENANT_ID, "orientation-dossier")).thenReturn(Optional.of(schema));

        TaskQuestionSchema result = useCase.getForTask(
                "task-1",
                "orientation-dossier",
                AccessContext.user("admin-1", Set.of("WORKFLOW_ADMIN")));

        assertThat(result).isEqualTo(schema);
    }

    private static TaskQuestionSchema schema(String schemaKey) {
        return new TaskQuestionSchema(
                UUID.randomUUID(),
                TENANT_ID,
                schemaKey,
                "Orientation",
                List.of(new FieldDefinition("decision", "Decision", FieldType.SELECT, true, null,
                        List.of("approve", "reject"), null)),
                "u",
                "u");
    }
}
