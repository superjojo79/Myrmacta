package com.flowforge.domain.workflow.port.in;

import java.util.Map;

public interface CompleteTaskUseCase {

    /**
     * @param actorUserId identity of the completing user — required to populate {@code
     *     TaskQuestionAnswer.submittedBy} for the {@code user-question} kind (D-USERQ-SUBMITTED-BY,
     *     TODO §55); {@code null} only for callers with no authenticated user in context.
     */
    void complete(String taskId, String dossierId, Map<String, Object> variables, String actorUserId);
}
