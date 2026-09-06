package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;

public interface DeleteTaskQuestionSchemaUseCase {

    void delete(String schemaKey, AccessContext.User actor);
}
