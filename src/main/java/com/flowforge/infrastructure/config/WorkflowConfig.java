package com.flowforge.infrastructure.config;

import com.flowforge.application.workflow.ManualStartOrchestrator;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierRelationsUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.dossier.usecase.AttachWorkflowUseCaseImpl;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.in.DeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.in.GetBpmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.GetDecisionTableDeploymentInfoUseCase;
import com.flowforge.domain.workflow.port.in.GetDmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.GetProcessDeploymentInfoUseCase;
import com.flowforge.domain.workflow.port.in.EvaluateDecisionUseCase;
import com.flowforge.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import com.flowforge.domain.workflow.port.in.ListDecisionTablesUseCase;
import com.flowforge.domain.workflow.port.in.RedeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.in.TestDecisionUseCase;
import com.flowforge.domain.workflow.port.in.ListMyTasksUseCase;
import com.flowforge.domain.workflow.port.in.SaveBpmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.SaveDmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.UpdateWorkflowLinkStatusUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnEvaluationPort;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowStartConfirmationPort;
import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.domain.workflow.port.out.WorkflowVisualizationPort;
import com.flowforge.domain.workflow.usecase.CompleteTaskUseCaseImpl;
import com.flowforge.domain.workflow.usecase.DeployBpmnProcessUseCaseImpl;
import com.flowforge.domain.workflow.usecase.DeployDecisionTableUseCaseImpl;
import com.flowforge.domain.workflow.usecase.GetBpmnDraftUseCaseImpl;
import com.flowforge.domain.workflow.usecase.GetDecisionTableDeploymentInfoUseCaseImpl;
import com.flowforge.domain.workflow.usecase.GetDmnDraftUseCaseImpl;
import com.flowforge.domain.workflow.usecase.GetProcessDeploymentInfoUseCaseImpl;
import com.flowforge.domain.workflow.usecase.GetTaskQuestionSchemaForTaskUseCaseImpl;
import com.flowforge.domain.workflow.usecase.EvaluateDecisionUseCaseImpl;
import com.flowforge.domain.workflow.usecase.ListDecisionTableVersionsUseCaseImpl;
import com.flowforge.domain.workflow.usecase.ListDecisionTablesUseCaseImpl;
import com.flowforge.domain.workflow.usecase.RedeployDecisionTableUseCaseImpl;
import com.flowforge.domain.workflow.usecase.TestDecisionUseCaseImpl;
import com.flowforge.domain.workflow.usecase.ListMyTasksUseCaseImpl;
import com.flowforge.domain.workflow.usecase.SaveBpmnDraftUseCaseImpl;
import com.flowforge.domain.workflow.usecase.SaveDmnDraftUseCaseImpl;
import com.flowforge.domain.workflow.usecase.TaskQuestionSchemaUseCaseImpl;
import com.flowforge.domain.workflow.usecase.UpdateWorkflowLinkStatusUseCaseImpl;
import com.flowforge.domain.workflow.usecase.WorkflowLinkVisualizationUseCaseImpl;
import com.flowforge.domain.workflow.usecase.WorkflowTypeConfigUseCaseImpl;
import com.flowforge.infrastructure.flowable.WorkflowStateEventListener;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
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
            WorkflowTypeConfigRepository repository,
            TenantAdminGuard tenantAdminGuard) {
        return new WorkflowTypeConfigUseCaseImpl(tenantProperties.id(), repository, tenantAdminGuard);
    }

    @Bean
    CompleteTaskUseCase completeTaskUseCase(
            TenantProperties tenantProperties,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            TaskQuestionSchemaRepository taskQuestionSchemaRepository,
            TaskQuestionAnswerRepository taskQuestionAnswerRepository,
            DossierWorkflowLinkRepository linkRepository,
            WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        return new CompleteTaskUseCaseImpl(
                tenantProperties.id(),
                workflowEnginePort,
                dossierRepository,
                dossierTypeRepository,
                taskQuestionSchemaRepository,
                taskQuestionAnswerRepository,
                linkRepository,
                participantPort,
                sendNotificationUseCase);
    }

    @Bean
    TaskQuestionSchemaUseCaseImpl taskQuestionSchemaUseCaseImpl(
            TenantProperties tenantProperties,
            TaskQuestionSchemaRepository repository,
            TenantAdminGuard tenantAdminGuard) {
        return new TaskQuestionSchemaUseCaseImpl(tenantProperties.id(), repository, tenantAdminGuard);
    }

    @Bean
    GetTaskQuestionSchemaForTaskUseCaseImpl getTaskQuestionSchemaForTaskUseCaseImpl(
            TenantProperties tenantProperties,
            TaskQuestionSchemaRepository schemaRepository,
            WorkflowEnginePort workflowEnginePort) {
        return new GetTaskQuestionSchemaForTaskUseCaseImpl(
                tenantProperties.id(), schemaRepository, workflowEnginePort);
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
            DossierAccessPolicy dossierAccessPolicy,
            SendNotificationUseCase sendNotificationUseCase) {
        return new WorkflowLinkVisualizationUseCaseImpl(
                tenantProperties.id(),
                linkRepository,
                dossierRepository,
                dossierTypeRepository,
                elementRepository,
                listDossierRelationsUseCase,
                visualizationPort,
                participantPort,
                dossierAccessPolicy,
                sendNotificationUseCase);
    }

    @Bean
    ManualStartOrchestrator manualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            WorkflowStartConfirmationPort workflowStartConfirmer,
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
            BpmnDraftRepository bpmnDraftRepository,
            DmnDeploymentPort dmnDeploymentPort) {
        return new DeployBpmnProcessUseCaseImpl(
                tenantProperties.id(),
                deploymentPort,
                configRepository,
                bpmnParserPort,
                bpmnDraftRepository,
                dmnDeploymentPort);
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
    DeployDecisionTableUseCase deployDecisionTableUseCase(
            TenantProperties tenantProperties,
            DmnDeploymentPort deploymentPort,
            DmnParserPort dmnParserPort,
            DmnDraftRepository dmnDraftRepository) {
        return new DeployDecisionTableUseCaseImpl(tenantProperties.id(), deploymentPort, dmnParserPort, dmnDraftRepository);
    }

    @Bean
    GetDecisionTableDeploymentInfoUseCase getDecisionTableDeploymentInfoUseCase(
            TenantProperties tenantProperties, DmnDeploymentPort deploymentPort) {
        return new GetDecisionTableDeploymentInfoUseCaseImpl(tenantProperties.id(), deploymentPort);
    }

    @Bean
    SaveDmnDraftUseCase saveDmnDraftUseCase(
            TenantProperties tenantProperties, DmnDraftRepository draftRepository, DmnParserPort dmnParserPort) {
        return new SaveDmnDraftUseCaseImpl(tenantProperties.id(), draftRepository, dmnParserPort);
    }

    @Bean
    GetDmnDraftUseCase getDmnDraftUseCase(TenantProperties tenantProperties, DmnDraftRepository draftRepository) {
        return new GetDmnDraftUseCaseImpl(tenantProperties.id(), draftRepository);
    }

    @Bean
    ListDecisionTablesUseCase listDecisionTablesUseCase(
            TenantProperties tenantProperties, DmnDeploymentPort deploymentPort) {
        return new ListDecisionTablesUseCaseImpl(tenantProperties.id(), deploymentPort);
    }

    @Bean
    EvaluateDecisionUseCase evaluateDecisionUseCase(
            TenantProperties tenantProperties,
            DmnDeploymentPort deploymentPort,
            DmnEvaluationPort evaluationPort,
            TenantAdminGuard tenantAdminGuard) {
        return new EvaluateDecisionUseCaseImpl(
                tenantProperties.id(), deploymentPort, evaluationPort, tenantAdminGuard);
    }

    @Bean
    TestDecisionUseCase testDecisionUseCase(
            EvaluateDecisionUseCase evaluateDecisionUseCase, TenantAdminGuard tenantAdminGuard) {
        return new TestDecisionUseCaseImpl(evaluateDecisionUseCase, tenantAdminGuard);
    }

    @Bean
    ListDecisionTableVersionsUseCase listDecisionTableVersionsUseCase(
            TenantProperties tenantProperties, DmnDeploymentPort deploymentPort, TenantAdminGuard tenantAdminGuard) {
        return new ListDecisionTableVersionsUseCaseImpl(tenantProperties.id(), deploymentPort, tenantAdminGuard);
    }

    @Bean
    RedeployDecisionTableUseCase redeployDecisionTableUseCase(
            TenantProperties tenantProperties,
            DmnDeploymentPort deploymentPort,
            DmnParserPort dmnParserPort,
            TenantAdminGuard tenantAdminGuard) {
        return new RedeployDecisionTableUseCaseImpl(
                tenantProperties.id(), deploymentPort, dmnParserPort, tenantAdminGuard);
    }

    @Bean
    ProcessEngineConfigurationConfigurer processEngineDatabaseTypeConfigurer() {
        return configuration -> configuration.setDatabaseType("postgres");
    }

    @Bean
    ProcessEngineConfigurationConfigurer workflowStateEventListenerConfigurer(WorkflowStateEventListener listener) {
        return configuration -> configuration.setEventListeners(java.util.List.of(listener));
    }

    // TODO §61 (introduction-dmn), M11/M12 -- works around a real, reproducible crash found while
    // proving M11's shipped fixture actually deploys (not just parses): with the default
    // enableSafeDmnXml=true, deploying ANY .dmn on this repository's pinned JDK 25 fails inside
    // Flowable 8.0.0 itself, not our code -- DmnParse.execute() -> DmnXMLConverter.convertToDmnModel
    // reuses a single XMLStreamReader across getTargetNameSpace() and the schema Validator (wraps it
    // in a javax.xml.transform.stax.StAXSource), and on this JDK the reader is no longer positioned
    // at START_DOCUMENT/START_ELEMENT by the time the Validator runs -- IllegalStateException,
    // wrapped as DmnXMLException("StAXSource(XMLStreamReader) with XMLStreamReader not in
    // XMLStreamConstants.START_DOCUMENT or XMLStreamConstants.START_ELEMENT state"). Verified by
    // decompiling flowable-dmn-engine/flowable-dmn-xml-converter 8.0.0 (javap -p -c), not assumed:
    // DmnEngineConfiguration.isEnableSafeDmnXml() has exactly one call site (DmnParse.execute()),
    // feeding the 3rd boolean of DmnXMLConverter#convertToDmnModel(InputStreamProvider, boolean,
    // boolean, String) -- true takes the buggy reader-reuse branch, false takes the branch that
    // re-opens a fresh InputStream for validation (the same branch FlowableDmnParserAdapter's own
    // extractDecisionKey already uses successfully, via the 3-arg overload that hardcodes this
    // argument to false). This is NOT a reduction of XXE protection: convertToDmnModel's safe
    // XMLInputFactory setup (isReplacingEntityReferences=false, isSupportingExternalEntities=false,
    // supportDTD=false, accessExternalDTD/accessExternalSchema="") is applied unconditionally at the
    // top of the method, before either branch -- isEnableSafeDmnXml only selects which branch reads
    // the stream, never whether XXE is disabled. No newer Flowable 8.0.x patch exists on Maven
    // Central to fix this upstream (checked : 8.0.0 is still latest/release).
    @Bean
    EngineConfigurationConfigurer<SpringDmnEngineConfiguration> dmnSafeXmlJdk25WorkaroundConfigurer() {
        return configuration -> configuration.setEnableSafeDmnXml(false);
    }
}
