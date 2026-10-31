package be.myrmacta.infrastructure.config;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.port.in.AttachWorkflowUseCase;
import be.myrmacta.domain.dossier.port.in.ListDossierRelationsUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.dossier.usecase.AttachWorkflowUseCaseImpl;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.DeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.in.GetBpmnDraftUseCase;
import be.myrmacta.domain.workflow.port.in.GetDecisionTableDeploymentInfoUseCase;
import be.myrmacta.domain.workflow.port.in.GetDmnDraftUseCase;
import be.myrmacta.domain.workflow.port.in.GetProcessDeploymentInfoUseCase;
import be.myrmacta.domain.workflow.port.in.EvaluateDecisionUseCase;
import be.myrmacta.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import be.myrmacta.domain.workflow.port.in.ListDecisionTablesUseCase;
import be.myrmacta.domain.workflow.port.in.RedeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.in.TestDecisionUseCase;
import be.myrmacta.domain.workflow.port.in.ListMyTasksUseCase;
import be.myrmacta.domain.workflow.port.in.SaveBpmnDraftUseCase;
import be.myrmacta.domain.workflow.port.in.SaveDmnDraftUseCase;
import be.myrmacta.domain.workflow.port.in.UpdateWorkflowLinkStatusUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnEvaluationPort;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.domain.workflow.port.out.IdempotencyStore;
import be.myrmacta.domain.workflow.port.out.TaskQuestionAnswerRepository;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowEventPublisher;
import be.myrmacta.domain.workflow.port.out.WorkflowStartConfirmationPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTaskInboxPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowVisualizationPort;
import be.myrmacta.domain.workflow.usecase.CompleteTaskUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.DeployBpmnProcessUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.DeployDecisionTableUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.GetBpmnDraftUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.GetDecisionTableDeploymentInfoUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.GetDmnDraftUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.GetProcessDeploymentInfoUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.GetTaskQuestionSchemaForTaskUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.EvaluateDecisionUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.ListDecisionTableVersionsUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.ListDecisionTablesUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.RedeployDecisionTableUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.TestDecisionUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.ListMyTasksUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.SaveBpmnDraftUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.SaveDmnDraftUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.TaskQuestionSchemaUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.UpdateWorkflowLinkStatusUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.WorkflowLinkVisualizationUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.WorkflowTypeConfigUseCaseImpl;
import be.myrmacta.infrastructure.flowable.WorkflowStateEventListener;
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
            DossierTypeRepository typeRepository,
            UserGroupsUseCase userGroups,
            DossierAccessPolicy dossierAccessPolicy) {
        return new ListMyTasksUseCaseImpl(
                tenantProperties.id(),
                inboxPort,
                workflowEnginePort,
                linkRepository,
                dossierRepository,
                typeRepository,
                java.time.Clock.systemUTC(),
                userGroups,
                dossierAccessPolicy);
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
            SendNotificationUseCase sendNotificationUseCase,
            KnownUserRepository knownUserRepository) {
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
                sendNotificationUseCase,
                knownUserRepository);
    }

    /** TODO §114 (D-GROUP-REASSIGN) — réaffecter une tâche à un autre groupe, tracé dans le dossier. */
    @Bean
    be.myrmacta.domain.workflow.usecase.ReassignTaskToGroupUseCaseImpl reassignTaskToGroupUseCase(
            TenantProperties tenantProperties,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            DossierAccessPolicy dossierAccessPolicy,
            WorkflowVisualizationPort visualizationPort,
            UserGroupsUseCase userGroups,
            be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository linkGroups,
            DossierElementRepository elementRepository) {
        return new be.myrmacta.domain.workflow.usecase.ReassignTaskToGroupUseCaseImpl(
                tenantProperties.id(), linkRepository, dossierRepository, dossierAccessPolicy, visualizationPort, userGroups, linkGroups,
                elementRepository, java.time.Clock.systemUTC());
    }

    /** TODO §94 — échecs des jobs du moteur rattachés aux dossiers (lecture, rejeu, supervision). */
    @Bean
    be.myrmacta.domain.workflow.usecase.WorkflowIncidentsUseCaseImpl workflowIncidentsUseCase(
            TenantProperties tenantProperties,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            WorkflowParticipantPort participantPort,
            DossierAccessPolicy dossierAccessPolicy,
            be.myrmacta.domain.workflow.port.out.WorkflowIncidentPort incidentPort) {
        return new be.myrmacta.domain.workflow.usecase.WorkflowIncidentsUseCaseImpl(
                tenantProperties.id(), linkRepository, dossierRepository, participantPort, dossierAccessPolicy, incidentPort);
    }

    @Bean
    ManualStartOrchestrator manualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            WorkflowStartConfirmationPort workflowStartConfirmer,
            WorkflowEventPublisher eventPublisher,
            be.myrmacta.domain.workflow.port.out.StartVariableCatalogPort startVariableCatalog,
            be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository linkGroups) {
        return new ManualStartOrchestrator(
                dossierRepository,
                dossierTypeRepository,
                linkRepository,
                attachWorkflow,
                workflowEnginePort,
                idempotencyStore,
                workflowStartConfirmer,
                eventPublisher,
                startVariableCatalog,
                linkGroups);
    }

    @Bean
    DeployBpmnProcessUseCase deployBpmnProcessUseCase(
            TenantProperties tenantProperties,
            WorkflowDeploymentPort deploymentPort,
            WorkflowTypeConfigRepository configRepository,
            BpmnParserPort bpmnParserPort,
            BpmnDraftRepository bpmnDraftRepository,
            DmnDeploymentPort dmnDeploymentPort,
            be.myrmacta.domain.workflow.portal.port.out.PortalAvailabilityPort portalAvailabilityPort,
            be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort signatureAvailabilityPort,
            be.myrmacta.domain.workflow.palette.BlockReferenceVerifier blockReferenceVerifier,
            be.myrmacta.domain.precheck.port.out.PrecheckAvailabilityPort precheckAvailability,
            be.myrmacta.domain.dossier.port.out.DossierTypeRepository dossierTypeRepository) {
        return new DeployBpmnProcessUseCaseImpl(
                tenantProperties.id(),
                deploymentPort,
                configRepository,
                bpmnParserPort,
                bpmnDraftRepository,
                dmnDeploymentPort,
                portalAvailabilityPort,
                signatureAvailabilityPort,
                blockReferenceVerifier,
                new be.myrmacta.domain.precheck.PrecheckDeploymentRules(
                        tenantProperties.id(), precheckAvailability, dossierTypeRepository));
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
