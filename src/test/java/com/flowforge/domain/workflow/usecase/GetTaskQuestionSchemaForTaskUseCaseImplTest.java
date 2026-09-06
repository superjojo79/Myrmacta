package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.workflow.TaskQuestionFormKeys;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTaskQuestionSchemaForTaskUseCaseImplTest {

    private static final String TENANT_ID = "tenant_dev";

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

        TaskQuestionSchema result = useCase.getForTask("task-1", "orientation-dossier");

        assertThat(result).isEqualTo(schema);
    }

    @Test
    void getForTask_rejects_whenTaskFormKeyReferencesADifferentSchema() {
        // D-USERQ-RUNTIME-SCOPE: a task holder must not be able to read an arbitrary schemaKey by
        // guessing it -- only the schema actually referenced by their own task's formKey.
        when(workflowEnginePort.findFormKeyByTaskId("task-1"))
                .thenReturn(Optional.of("user-question:other-schema"));

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier"))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }

    @Test
    void getForTask_rejects_whenTaskHasNoFormKey() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier"))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }

    @Test
    void getForTask_rejects_whenTaskFormKeyIsADifferentKind() {
        when(workflowEnginePort.findFormKeyByTaskId("task-1")).thenReturn(Optional.of("document-generation"));

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier"))
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

        assertThatThrownBy(() -> useCase.getForTask("task-1", "orientation-dossier"))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
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
