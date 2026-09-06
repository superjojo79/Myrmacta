package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import java.util.List;
import java.util.Optional;

public interface TaskQuestionSchemaRepository {

    TaskQuestionSchema save(TaskQuestionSchema schema);

    Optional<TaskQuestionSchema> findByKey(String tenantId, String schemaKey);

    List<TaskQuestionSchema> findAll(String tenantId);

    void delete(String tenantId, String schemaKey);

    default PageResult<TaskQuestionSchema> page(String tenantId, PageQuery query) {
        return PageResult.slice(findAll(tenantId), query);
    }
}
