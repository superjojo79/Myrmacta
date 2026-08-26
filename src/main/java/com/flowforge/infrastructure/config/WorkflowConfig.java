package com.flowforge.infrastructure.config;

import com.flowforge.application.workflow.ManualStartOrchestrator;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierRelationsUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.dossier.usecase.AttachWorkflowUseCaseImpl;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.in.GetBpmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.GetProcessDeploymentInfoUseCase;
import com.flowforge.domain.workflow.port.in.ListMyTasksUseCase;
import com.flowforge.domain.workflow.port.in.SaveBpmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.UpdateWorkflowLinkStatusUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.domain.workflow.port.out.WorkflowVisualizationPort;
import com.flowforge.domain.workflow.usecase.CompleteTaskUseCaseImpl;
import com.flowforge.domain.workflow.usecase.DeployBpmnProcessUseCaseImpl;
import com.flowforge.domain.workflow.usecase.GetBpmnDraftUseCaseImpl;
import com.flowforge.domain.workflow.usecase.GetProcessDeploymentInfoUseCaseImpl;
import com.flowforge.domain.workflow.usecase.ListMyTasksUseCaseImpl;
import com.flowforge.domain.workflow.usecase.SaveBpmnDraftUseCaseImpl;
import com.flowforge.domain.workflow.usecase.UpdateWorkflowLinkStatusUseCaseImpl;
import com.flowforge.domain.workflow.usecase.WorkflowLinkVisualizationUseCaseImpl;
import com.flowforge.domain.workflow.usecase.WorkflowTypeConfigUseCaseImpl;
import com.flowforge.infrastructure.flowable.WorkflowStateEventListener;
import com.flowforge.infrastructure.workflow.WorkflowStartConfirmer;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({WorkflowProperties.class, BpmnUploadProperties.class})
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
    ListMyTasksUseCase listMyTasksUseCase(
            TenantProperties tenantProperties,
            WorkflowTaskInboxPort inboxPort,
            WorkflowEnginePort workflowEnginePort,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository) {
        return new ListMyTasksUseCaseImpl(
                tenantProperties.id(),
                inboxPort,
                workflowEnginePort,
                linkRepository,
                dossierRepository,
                typeRepository,
                java.time.Clock.systemUTC());
    }

    @Bean
    UpdateWorkflowLinkStatusUseCase updateWorkflowLinkStatusUseCase(
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            WorkflowTypeConfigRepository configRepository) {
        return new UpdateWorkflowLinkStatusUseCaseImpl(linkRepository, dossierRepository, configRepository);
    }

    @Bean
    WorkflowLinkVisualizationUseCaseImpl workflowLinkVisualizationUseCaseImpl(
            TenantProperties tenantProperties,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            DossierElementRepository elementRepository,
            ListDossierRelationsUseCase listDossierRelationsUseCase,
            WorkflowVisualizationPort visualizationPort,
            WorkflowParticipantPort participantPort,
            DossierAccessPolicy dossierAccessPolicy) {
        return new WorkflowLinkVisualizationUseCaseImpl(
                tenantProperties.id(),
                linkRepository,
                dossierRepository,
                dossierTypeRepository,
                elementRepository,
                listDossierRelationsUseCase,
                visualizationPort,
                participantPort,
                dossierAccessPolicy);
    }

    @Bean
    ManualStartOrchestrator manualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            WorkflowStartConfirmer workflowStartConfirmer,
            WorkflowEventPublisher eventPublisher) {
        return new ManualStartOrchestrator(
                dossierRepository,
                linkRepository,
                attachWorkflow,
                workflowEnginePort,
                idempotencyStore,
                workflowStartConfirmer,
                eventPublisher);
    }

    @Bean
    DeployBpmnProcessUseCase deployBpmnProcessUseCase(
            TenantProperties tenantProperties,
            WorkflowDeploymentPort deploymentPort,
            WorkflowTypeConfigRepository configRepository,
            BpmnParserPort bpmnParserPort,
            BpmnDraftRepository bpmnDraftRepository) {
        return new DeployBpmnProcessUseCaseImpl(
                tenantProperties.id(), deploymentPort, configRepository, bpmnParserPort, bpmnDraftRepository);
    }

    @Bean
    GetProcessDeploymentInfoUseCase getProcessDeploymentInfoUseCase(
            TenantProperties tenantProperties, WorkflowDeploymentPort deploymentPort) {
        return new GetProcessDeploymentInfoUseCaseImpl(tenantProperties.id(), deploymentPort);
    }

    @Bean
    SaveBpmnDraftUseCase saveBpmnDraftUseCase(
            TenantProperties tenantProperties, BpmnDraftRepository draftRepository, BpmnParserPort bpmnParserPort) {
        return new SaveBpmnDraftUseCaseImpl(tenantProperties.id(), draftRepository, bpmnParserPort);
    }

    @Bean
    GetBpmnDraftUseCase getBpmnDraftUseCase(TenantProperties tenantProperties, BpmnDraftRepository draftRepository) {
        return new GetBpmnDraftUseCaseImpl(tenantProperties.id(), draftRepository);
    }

    @Bean
    ProcessEngineConfigurationConfigurer processEngineDatabaseTypeConfigurer() {
        return configuration -> configuration.setDatabaseType("postgres");
    }

    @Bean
    ProcessEngineConfigurationConfigurer workflowStateEventListenerConfigurer(WorkflowStateEventListener listener) {
        return configuration -> configuration.setEventListeners(java.util.List.of(listener));
    }
}
