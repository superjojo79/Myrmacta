package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import java.util.Optional;

/**
 * Admin read (includes {@code createdBy}/{@code updatedBy}) — see
 * {@link GetTaskQuestionSchemaForTaskUseCase} for the runtime-scoped equivalent. {@code actor} is
 * required on every method — mirrors {@code TaskQuestionSchemaController}, which gates its reads
 * ({@code list}/{@code getByKey}) the same as its writes, unlike {@code WorkflowTypeConfigUseCase}
 * (extension-mcp-flux-metier, D5).
 */
public interface GetTaskQuestionSchemaUseCase {

    Optional<TaskQuestionSchema> getByKey(String schemaKey, AccessContext.User actor);

    PageResult<TaskQuestionSchema> list(PageQuery query, AccessContext.User actor);
}
