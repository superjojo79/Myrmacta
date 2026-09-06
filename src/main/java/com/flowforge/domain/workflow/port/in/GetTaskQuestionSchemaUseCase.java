package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
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
