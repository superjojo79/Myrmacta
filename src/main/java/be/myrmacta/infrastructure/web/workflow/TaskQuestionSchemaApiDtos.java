package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import java.util.List;
import java.util.UUID;

record CreateTaskQuestionSchemaRequest(String schemaKey, String label, List<FieldDefinition> fieldDefinitions) {}

record UpdateTaskQuestionSchemaRequest(String label, List<FieldDefinition> fieldDefinitions) {}

/** Admin response — includes audit fields, {@code TENANT_ADMIN}/{@code WORKFLOW_ADMIN} only. */
record TaskQuestionSchemaResponse(
        UUID id,
        String schemaKey,
        String label,
        List<FieldDefinition> fieldDefinitions,
        String createdBy,
        String updatedBy) {

    static TaskQuestionSchemaResponse from(TaskQuestionSchema schema) {
        return new TaskQuestionSchemaResponse(
                schema.id(), schema.schemaKey(), schema.label(), schema.fields(), schema.createdBy(), schema.updatedBy());
    }
}

/** Runtime response for the task holder (D-USERQ-RUNTIME-SCOPE) — audit fields never included. */
record TaskQuestionSchemaRuntimeResponse(String schemaKey, String label, List<FieldDefinition> fieldDefinitions) {

    static TaskQuestionSchemaRuntimeResponse from(TaskQuestionSchema schema) {
        return new TaskQuestionSchemaRuntimeResponse(schema.schemaKey(), schema.label(), schema.fields());
    }
}
