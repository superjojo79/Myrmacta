package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.TaskQuestionAnswer;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaTaskQuestionAnswerRepositoryAdapter implements TaskQuestionAnswerRepository {

    private final SpringDataTaskQuestionAnswerRepository repository;

    JpaTaskQuestionAnswerRepositoryAdapter(SpringDataTaskQuestionAnswerRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(TaskQuestionAnswer answer) {
        repository.save(TaskQuestionAnswerJpaEntity.fromDomain(answer));
    }
}
