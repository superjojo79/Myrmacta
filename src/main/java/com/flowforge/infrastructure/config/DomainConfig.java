package com.flowforge.infrastructure.config;

import com.flowforge.domain.workflow.port.in.StartWorkflowUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowRepository;
import com.flowforge.domain.workflow.usecase.StartWorkflowUseCaseImpl;
import com.flowforge.infrastructure.flowable.InMemoryWorkflowEventPublisher;
import com.flowforge.infrastructure.persistence.workflow.InMemoryWorkflowRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Câblage hexagonal : expose les ports sortants in-memory et le use case via son
 * interface {@link StartWorkflowUseCase}. Aucune annotation Spring dans {@code domain}.
 * Remplacer les beans in-memory par JPA/Flowable dans les mêmes packages cibles.
 */
@Configuration
public class DomainConfig {

    @Bean
    WorkflowRepository workflowRepository() {
        return new InMemoryWorkflowRepository();
    }

    @Bean
    WorkflowEventPublisher workflowEventPublisher() {
        return new InMemoryWorkflowEventPublisher();
    }

    @Bean
    StartWorkflowUseCase startWorkflowUseCase(
            WorkflowRepository workflowRepository,
            WorkflowEventPublisher workflowEventPublisher) {
        return new StartWorkflowUseCaseImpl(workflowRepository, workflowEventPublisher);
    }
}
