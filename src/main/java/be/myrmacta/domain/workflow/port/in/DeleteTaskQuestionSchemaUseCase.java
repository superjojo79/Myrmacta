package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;

public interface DeleteTaskQuestionSchemaUseCase {

    void delete(String schemaKey, AccessContext.User actor);
}
