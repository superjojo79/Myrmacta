package com.flowforge.infrastructure.persistence.workflow;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTaskQuestionAnswerRepository extends JpaRepository<TaskQuestionAnswerJpaEntity, UUID> {}
