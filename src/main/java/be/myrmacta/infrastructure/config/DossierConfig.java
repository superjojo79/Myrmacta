package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.DossierUniquenessCheck;
import be.myrmacta.domain.expression.port.out.ExpressionEnginePort;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.RetentionPolicyResolver;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.model.DossierFieldValidator;
import be.myrmacta.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.EvaluateDossierRulesUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.EnqueueDossierPurgeUseCase;
import be.myrmacta.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import be.myrmacta.domain.dossier.port.in.ExportDossiersUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierElementUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import be.myrmacta.domain.dossier.port.in.GetTenantRetentionSettingsUseCase;
import be.myrmacta.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import be.myrmacta.domain.dossier.port.in.ListDossierElementsUseCase;
import be.myrmacta.domain.dossier.port.in.ListDossierRelationsUseCase;
import be.myrmacta.domain.dossier.port.in.ListDossierTypesUseCase;
import be.myrmacta.domain.dossier.port.in.ListPurgeOutboxUseCase;
import be.myrmacta.domain.dossier.port.in.RevokeDossierTypeAccessUseCase;
import be.myrmacta.domain.dossier.port.in.SearchDossiersUseCase;
import be.myrmacta.domain.dossier.port.in.SearchSimilarDossiersUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.UpsertTenantRetentionSettingsUseCase;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierExportPort;
import be.myrmacta.domain.dossier.port.out.DossierReferenceGenerator;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierUniquenessPort;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
import be.myrmacta.domain.dossier.port.out.TenantRetentionSettingsRepository;
import be.myrmacta.domain.dossier.usecase.AddDossierElementUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.CheckDossierTypeSchemaCompatibilityUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.CreateDossierRelationUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.CreateDossierTypeUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.EvaluateDossierRulesUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.CreateDossierUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.EnqueueDossierPurgeUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.EvaluateDossierRetentionUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.ExportDossiersUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.GetDossierElementUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.GetDossierTypeUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.GetDossierUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.GetTenantRetentionSettingsUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.GrantDossierTypeAccessUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.ListDossierElementsUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.ListDossierRelationsUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.ListDossierTypesUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.ListPurgeOutboxUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.ProcessPurgeOutboxUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.RevokeDossierTypeAccessUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.SearchDossiersUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.SearchSimilarDossiersUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.UpdateDossierFieldsUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.UpdateDossierStatusUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.UpdateDossierTypeUseCaseImpl;
import be.myrmacta.domain.dossier.usecase.UpsertTenantRetentionSettingsUseCaseImpl;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.infrastructure.dossier.ClamAvAttachmentScanAdapter;
import be.myrmacta.infrastructure.dossier.NoOpAttachmentScanAdapter;
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

    /** TODO §102 (issue #184, lot B) — brique « Rechercher des dossiers ». */
    @Bean
    SearchSimilarDossiersUseCase searchSimilarDossiersUseCase(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            DossierSearchPort dossierSearchPort) {
        return new SearchSimilarDossiersUseCaseImpl(dossierRepository, typeRepository, dossierSearchPort);
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
            be.myrmacta.domain.workflow.port.out.TaskQuestionAnswerRepository taskQuestionAnswerRepository,
            be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository portalLinkRepository,
            be.myrmacta.domain.signature.port.out.SignatureRequestRepository signatureRequestRepository) {
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
