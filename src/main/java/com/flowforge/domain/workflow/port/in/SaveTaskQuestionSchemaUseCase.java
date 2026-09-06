package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;

public interface SaveTaskQuestionSchemaUseCase {

    TaskQuestionSchema save(TaskQuestionSchema schema, AccessContext.User actor);
}
