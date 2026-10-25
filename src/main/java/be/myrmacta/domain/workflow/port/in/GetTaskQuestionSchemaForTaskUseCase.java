package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;

/**
 * Runtime read for the task holder (D-USERQ-RUNTIME-SCOPE, TODO §55) — audit fields excluded,
 * and only served if {@code taskId}'s server-side {@code formKey} actually references
 * {@code schemaKey}, since a {@code TaskQuestionSchema} has no {@code DossierType}/{@code VIEW}
 * permission to check against (unlike {@code document-generation}).
 */
public interface GetTaskQuestionSchemaForTaskUseCase {

    TaskQuestionSchema getForTask(String taskId, String schemaKey, AccessContext.User actor);
}
