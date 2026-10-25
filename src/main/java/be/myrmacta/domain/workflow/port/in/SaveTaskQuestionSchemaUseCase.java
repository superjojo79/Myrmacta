package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;

public interface SaveTaskQuestionSchemaUseCase {

    TaskQuestionSchema save(TaskQuestionSchema schema, AccessContext.User actor);
}
