package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.TaskQuestionAnswer;

public interface TaskQuestionAnswerRepository {

    void save(TaskQuestionAnswer answer);
}
