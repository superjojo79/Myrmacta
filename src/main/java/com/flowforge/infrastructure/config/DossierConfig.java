package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.DossierFieldRules;
import com.flowforge.domain.dossier.DossierUniquenessCheck;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.RetentionPolicyResolver;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierRelationUseCase;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.EvaluateDossierRulesUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.EnqueueDossierPurgeUseCase;
import com.flowforge.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import com.flowforge.domain.dossier.port.in.ExportDossiersUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierElementUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import com.flowforge.domain.dossier.port.in.GetTenantRetentionSettingsUseCase;
import com.flowforge.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierElementsUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierRelationsUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierTypesUseCase;
import com.flowforge.domain.dossier.port.in.ListPurgeOutboxUseCase;
import com.flowforge.domain.dossier.port.in.RevokeDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.in.SearchDossiersUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.UpsertTenantRetentionSettingsUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierExportPort;
import com.flowforge.domain.dossier.port.out.DossierReferenceGenerator;
import com.flowforge.domain.dossier.port.out.DossierRelationRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierUniquenessPort;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.dossier.port.out.TenantRetentionSettingsRepository;
import com.flowforge.domain.dossier.usecase.AddDossierElementUseCaseImpl;
import com.flowforge.domain.dossier.usecase.CheckDossierTypeSchemaCompatibilityUseCaseImpl;
import com.flowforge.domain.dossier.usecase.CreateDossierRelationUseCaseImpl;
import com.flowforge.domain.dossier.usecase.CreateDossierTypeUseCaseImpl;
import com.flowforge.domain.dossier.usecase.EvaluateDossierRulesUseCaseImpl;
import com.flowforge.domain.dossier.usecase.CreateDossierUseCaseImpl;
import com.flowforge.domain.dossier.usecase.EnqueueDossierPurgeUseCaseImpl;
import com.flowforge.domain.dossier.usecase.EvaluateDossierRetentionUseCaseImpl;
import com.flowforge.domain.dossier.usecase.ExportDossiersUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GetDossierElementUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GetDossierTypeUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GetDossierUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GetTenantRetentionSettingsUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GrantDossierTypeAccessUseCaseImpl;
import com.flowforge.domain.dossier.usecase.ListDossierElementsUseCaseImpl;
import com.flowforge.domain.dossier.usecase.ListDossierRelationsUseCaseImpl;
import com.flowforge.domain.dossier.usecase.ListDossierTypesUseCaseImpl;
import com.flowforge.domain.dossier.usecase.ListPurgeOutboxUseCaseImpl;
import com.flowforge.domain.dossier.usecase.ProcessPurgeOutboxUseCaseImpl;
import com.flowforge.domain.dossier.usecase.RevokeDossierTypeAccessUseCaseImpl;
import com.flowforge.domain.dossier.usecase.SearchDossiersUseCaseImpl;
import com.flowforge.domain.dossier.usecase.UpdateDossierFieldsUseCaseImpl;
import com.flowforge.domain.dossier.usecase.UpdateDossierStatusUseCaseImpl;
import com.flowforge.domain.dossier.usecase.UpdateDossierTypeUseCaseImpl;
import com.flowforge.domain.dossier.usecase.UpsertTenantRetentionSettingsUseCaseImpl;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.infrastructure.dossier.ClamAvAttachmentScanAdapter;
import com.flowforge.infrastructure.dossier.NoOpAttachmentScanAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    AttachmentUploadProperties.class,
    AttachmentScanProperties.class,
    RetentionProperties.class,
    PurgeProperties.class
})
public class DossierConfig {

    @Bean
    TenantAdminGuard tenantAdminGuard() {
        return new TenantAdminGuard();
    }

    @Bean
    @ConditionalOnProperty(name = "app.dossier.attachment.scan.type", havingValue = "noop", matchIfMissing = true)
    AttachmentScanPort noOpAttachmentScanPort() {
        return new NoOpAttachmentScanAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.dossier.attachment.scan.type", havingValue = "clamav")
    AttachmentScanPort clamAvAttachmentScanPort(AttachmentScanProperties properties, MeterRegistry meterRegistry) {
        return new ClamAvAttachmentScanAdapter(properties.clamav(), meterRegistry);
    }

    @Bean
    DossierAccessPolicy dossierAccessPolicy(DossierTypeAclRepository aclRepository) {
        return new DossierAccessPolicy(aclRepository);
    }

    /** TODO §80 : champs calculés, visibilité et obligation conditionnelles, évalués par le moteur d'expressions borné (LC2). */
    @Bean
    DossierFieldRules dossierFieldRules(ExpressionEnginePort expressionEngine) {
        return new DossierFieldRules(expressionEngine);
    }

    @Bean
    EvaluateDossierRulesUseCase evaluateDossierRulesUseCase(
            DossierTypeRepository typeRepository, DossierAccessPolicy accessPolicy, DossierFieldRules fieldRules) {
        return new EvaluateDossierRulesUseCaseImpl(typeRepository, accessPolicy, fieldRules);
    }

    @Bean
    CreateDossierTypeUseCase createDossierTypeUseCase(
            DossierTypeRepository typeRepository, TenantAdminGuard tenantAdminGuard, DossierFieldRules fieldRules) {
        return new CreateDossierTypeUseCaseImpl(typeRepository, tenantAdminGuard, fieldRules);
    }

    @Bean
    UpdateDossierTypeUseCase updateDossierTypeUseCase(
            DossierTypeRepository typeRepository,
            TenantAdminGuard tenantAdminGuard,
            CheckDossierTypeSchemaCompatibilityUseCase checkDossierTypeSchemaCompatibilityUseCase,
            DossierFieldRules fieldRules) {
        return new UpdateDossierTypeUseCaseImpl(
                typeRepository, tenantAdminGuard, checkDossierTypeSchemaCompatibilityUseCase, fieldRules);
    }

    @Bean
    CheckDossierTypeSchemaCompatibilityUseCase checkDossierTypeSchemaCompatibilityUseCase(
            DossierRepository dossierRepository, DossierFieldRules fieldRules) {
        return new CheckDossierTypeSchemaCompatibilityUseCaseImpl(dossierRepository, new DossierFieldValidator(), fieldRules);
    }

    @Bean
    GetDossierTypeUseCase getDossierTypeUseCase(
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy) {
        return new GetDossierTypeUseCaseImpl(typeRepository, accessPolicy);
    }

    @Bean
    ListDossierTypesUseCase listDossierTypesUseCase(
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository) {
        return new ListDossierTypesUseCaseImpl(typeRepository, aclRepository);
    }

    @Bean
    GrantDossierTypeAccessUseCase grantDossierTypeAccessUseCase(
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository,
            TenantAdminGuard tenantAdminGuard) {
        return new GrantDossierTypeAccessUseCaseImpl(typeRepository, aclRepository, tenantAdminGuard);
    }

    @Bean
    RevokeDossierTypeAccessUseCase revokeDossierTypeAccessUseCase(
            DossierTypeAclRepository aclRepository, TenantAdminGuard tenantAdminGuard) {
        return new RevokeDossierTypeAccessUseCaseImpl(aclRepository, tenantAdminGuard);
    }

    @Bean
    CreateDossierUseCase createDossierUseCase(
            DossierTypeRepository typeRepository,
            DossierRepository dossierRepository,
            DossierReferenceGenerator referenceGenerator,
            DossierAccessPolicy accessPolicy,
            DossierFieldRules fieldRules,
            DossierUniquenessPort uniquenessPort) {
        return new CreateDossierUseCaseImpl(typeRepository, dossierRepository, referenceGenerator, accessPolicy,
                new DossierFieldValidator(), fieldRules, new DossierUniquenessCheck(uniquenessPort));
    }

    @Bean
    GetDossierUseCase getDossierUseCase(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy) {
        return new GetDossierUseCaseImpl(dossierRepository, typeRepository, accessPolicy);
    }

    @Bean
    SearchDossiersUseCase searchDossiersUseCase(
            DossierSearchPort dossierSearchPort,
            DossierTypeAclRepository aclRepository,
            DossierTypeRepository typeRepository) {
        return new SearchDossiersUseCaseImpl(dossierSearchPort, aclRepository, typeRepository);
    }

    @Bean
    ExportDossiersUseCase exportDossiersUseCase(
            DossierTypeRepository typeRepository,
            DossierExportPort dossierExportPort,
            DossierAccessPolicy accessPolicy) {
        return new ExportDossiersUseCaseImpl(typeRepository, dossierExportPort, accessPolicy);
    }

    @Bean
    ListDossierElementsUseCase listDossierElementsUseCase(
            DossierRepository dossierRepository,
            DossierElementRepository dossierElementRepository,
            DossierAccessPolicy accessPolicy) {
        return new ListDossierElementsUseCaseImpl(
                dossierRepository, dossierElementRepository, accessPolicy);
    }

    @Bean
    UpdateDossierStatusUseCase updateDossierStatusUseCase(
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy) {
        return new UpdateDossierStatusUseCaseImpl(dossierRepository, accessPolicy);
    }

    @Bean
    UpdateDossierFieldsUseCase updateDossierFieldsUseCase(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy,
            DossierFieldRules fieldRules,
            DossierUniquenessPort uniquenessPort) {
        return new UpdateDossierFieldsUseCaseImpl(dossierRepository, typeRepository, accessPolicy,
                new DossierFieldValidator(), fieldRules, new DossierUniquenessCheck(uniquenessPort));
    }

    @Bean
    AddDossierElementUseCase addDossierElementUseCase(
            TenantProperties tenantProperties,
            DossierRepository dossierRepository,
            DossierElementRepository dossierElementRepository,
            DossierAccessPolicy accessPolicy,
            DossierWorkflowLinkRepository linkRepository,
            WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        return new AddDossierElementUseCaseImpl(
                tenantProperties.id(),
                dossierRepository,
                dossierElementRepository,
                accessPolicy,
                linkRepository,
                participantPort,
                sendNotificationUseCase);
    }

    @Bean
    GetDossierElementUseCase getDossierElementUseCase(
            TenantProperties tenantProperties,
            DossierElementRepository dossierElementRepository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy) {
        return new GetDossierElementUseCaseImpl(
                tenantProperties.id(), dossierElementRepository, dossierRepository, accessPolicy);
    }

    @Bean
    CreateDossierRelationUseCase createDossierRelationUseCase(
            DossierRepository dossierRepository,
            DossierRelationRepository dossierRelationRepository,
            DossierAccessPolicy accessPolicy) {
        return new CreateDossierRelationUseCaseImpl(
                dossierRepository, dossierRelationRepository, accessPolicy);
    }

    @Bean
    ListDossierRelationsUseCase listDossierRelationsUseCase(
            DossierRepository dossierRepository,
            DossierRelationRepository dossierRelationRepository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy) {
        return new ListDossierRelationsUseCaseImpl(
                dossierRepository, dossierRelationRepository, typeRepository, accessPolicy);
    }

    @Bean
    RetentionPolicyResolver retentionPolicyResolver() {
        return new RetentionPolicyResolver();
    }

    @Bean
    GetTenantRetentionSettingsUseCase getTenantRetentionSettingsUseCase(
            TenantRetentionSettingsRepository repository, TenantAdminGuard tenantAdminGuard) {
        return new GetTenantRetentionSettingsUseCaseImpl(repository, tenantAdminGuard);
    }

    @Bean
    UpsertTenantRetentionSettingsUseCase upsertTenantRetentionSettingsUseCase(
            TenantRetentionSettingsRepository repository, TenantAdminGuard tenantAdminGuard) {
        return new UpsertTenantRetentionSettingsUseCaseImpl(repository, tenantAdminGuard);
    }

    @Bean
    EvaluateDossierRetentionUseCase evaluateDossierRetentionUseCase(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            TenantRetentionSettingsRepository settingsRepository,
            PurgeOutboxRepository purgeOutboxRepository,
            RetentionPolicyResolver retentionPolicyResolver) {
        return new EvaluateDossierRetentionUseCaseImpl(
                dossierRepository,
                typeRepository,
                settingsRepository,
                purgeOutboxRepository,
                retentionPolicyResolver);
    }

    @Bean
    ProcessPurgeOutboxUseCase processPurgeOutboxUseCase(
            PurgeOutboxRepository purgeOutboxRepository,
            DossierRepository dossierRepository,
            DossierElementRepository dossierElementRepository,
            DossierRelationRepository dossierRelationRepository,
            DossierWorkflowLinkRepository dossierWorkflowLinkRepository,
            DocumentStoragePort documentStoragePort,
            WorkflowEnginePort workflowEnginePort,
            com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository taskQuestionAnswerRepository,
            com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository portalLinkRepository,
            com.flowforge.domain.signature.port.out.SignatureRequestRepository signatureRequestRepository) {
        return new ProcessPurgeOutboxUseCaseImpl(
                purgeOutboxRepository,
                dossierRepository,
                dossierElementRepository,
                dossierRelationRepository,
                dossierWorkflowLinkRepository,
                documentStoragePort,
                workflowEnginePort,
                taskQuestionAnswerRepository,
                portalLinkRepository,
                signatureRequestRepository);
    }

    @Bean
    EnqueueDossierPurgeUseCase enqueueDossierPurgeUseCase(
            DossierRepository dossierRepository,
            PurgeOutboxRepository purgeOutboxRepository,
            TenantAdminGuard tenantAdminGuard) {
        return new EnqueueDossierPurgeUseCaseImpl(dossierRepository, purgeOutboxRepository, tenantAdminGuard);
    }

    @Bean
    ListPurgeOutboxUseCase listPurgeOutboxUseCase(
            PurgeOutboxRepository purgeOutboxRepository, TenantAdminGuard tenantAdminGuard) {
        return new ListPurgeOutboxUseCaseImpl(purgeOutboxRepository, tenantAdminGuard);
    }
}
