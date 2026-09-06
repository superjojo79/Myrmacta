package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import java.util.Optional;

/** Admin read (includes {@code createdBy}/{@code updatedBy}) — see {@link GetTaskQuestionSchemaForTaskUseCase} for the runtime-scoped equivalent. */
public interface GetTaskQuestionSchemaUseCase {

    Optional<TaskQuestionSchema> getByKey(String schemaKey);

    PageResult<TaskQuestionSchema> list(PageQuery query);
}
