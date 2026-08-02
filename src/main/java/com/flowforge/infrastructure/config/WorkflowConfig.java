package com.flowforge.infrastructure.config;

import com.flowforge.application.workflow.ManualStartOrchestrator;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.dossier.usecase.AttachWorkflowUseCaseImpl;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.in.UpdateWorkflowLinkStatusUseCase;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.domain.workflow.usecase.CompleteTaskUseCaseImpl;
import com.flowforge.domain.workflow.usecase.UpdateWorkflowLinkStatusUseCaseImpl;
import com.flowforge.domain.workflow.usecase.WorkflowTypeConfigUseCaseImpl;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowConfig {

    @Bean
    AttachWorkflowUseCaseImpl attachWorkflowUseCaseImpl(
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            WorkflowTypeConfigRepository configRepository) {
        return new AttachWorkflowUseCaseImpl(linkRepository, dossierRepository, configRepository);
    }

    @Bean
    WorkflowTypeConfigUseCaseImpl workflowTypeConfigUseCaseImpl(
            TenantProperties tenantProperties,
            WorkflowTypeConfigRepository repository) {
        return new WorkflowTypeConfigUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    CompleteTaskUseCase completeTaskUseCase(WorkflowEnginePort workflowEnginePort) {
        return new CompleteTaskUseCaseImpl(workflowEnginePort);
    }

    @Bean
    UpdateWorkflowLinkStatusUseCase updateWorkflowLinkStatusUseCase(
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            WorkflowTypeConfigRepository configRepository) {
        return new UpdateWorkflowLinkStatusUseCaseImpl(linkRepository, dossierRepository, configRepository);
    }

    @Bean
    ManualStartOrchestrator manualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierWorkflowLinkRepository linkRepository,
            WorkflowTypeConfigRepository configRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            UpdateDossierStatusUseCase updateDossierStatusUseCase,
            WorkflowEventPublisher eventPublisher) {
        return new ManualStartOrchestrator(
                dossierRepository,
                linkRepository,
                configRepository,
                attachWorkflow,
                workflowEnginePort,
                idempotencyStore,
                updateDossierStatusUseCase,
                eventPublisher);
    }

    @Bean
    ProcessEngineConfigurationConfigurer processEngineDatabaseTypeConfigurer() {
        return configuration -> configuration.setDatabaseType("postgres");
    }
}
