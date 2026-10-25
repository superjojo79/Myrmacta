package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.promotion.port.in.ExportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.promotion.port.out.ConfigImportLogRepository;
import be.myrmacta.domain.promotion.port.out.ConfigurationBundleCodec;
import be.myrmacta.domain.promotion.usecase.ExportConfigurationUseCaseImpl;
import be.myrmacta.domain.promotion.usecase.ImportConfigurationUseCaseImpl;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.promotion.JsonArtifactCodec;
import be.myrmacta.infrastructure.promotion.ZipConfigurationBundleCodec;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §83 (D4) — export, plan à blanc et import de bundles de configuration. */
@Configuration
class PromotionConfig {

    @Bean
    ArtifactCodec artifactCodec() {
        return new JsonArtifactCodec();
    }

    @Bean
    ConfigurationBundleCodec configurationBundleCodec() {
        return new ZipConfigurationBundleCodec();
    }

    @Bean
    ExportConfigurationUseCase exportConfigurationUseCase(
            TenantProperties tenantProperties,
            @Value("${app.version:}") String appVersion,
            TenantAdminGuard guard,
            ArtifactCodec codec,
            DossierTypeRepository types,
            TaskQuestionSchemaRepository schemas,
            ConnectorInstanceRepository connectors,
            ConnectorCatalog catalog,
            DmnDeploymentPort dmnDeployment,
            WorkflowTypeConfigRepository workflowConfigs,
            WorkflowDeploymentPort workflowDeployment) {
        return new ExportConfigurationUseCaseImpl(tenantProperties.id(), appVersion, Clock.systemUTC(), guard, codec, types, schemas,
                connectors, catalog, dmnDeployment, workflowConfigs, workflowDeployment);
    }

    @Bean
    ImportConfigurationUseCase importConfigurationUseCase(
            TenantProperties tenantProperties,
            TenantAdminGuard guard,
            ArtifactCodec codec,
            DossierTypeRepository types,
            CreateDossierTypeUseCase createType,
            UpdateDossierTypeUseCase updateType,
            CheckDossierTypeSchemaCompatibilityUseCase compatibility,
            TaskQuestionSchemaRepository schemas,
            SaveTaskQuestionSchemaUseCase saveSchema,
            ConnectorInstanceRepository connectors,
            ManageConnectorInstanceUseCase manageConnector,
            ConnectorCatalog catalog,
            DmnDeploymentPort dmnDeployment,
            DmnParserPort dmnParser,
            WorkflowDeploymentPort workflowDeployment,
            BpmnParserPort bpmnParser,
            WorkflowTypeConfigRepository workflowConfigs,
            SaveWorkflowTypeConfigUseCase saveWorkflowConfig,
            ProposeDraftUseCase propose,
            ConfigImportLogRepository journal) {
        return new ImportConfigurationUseCaseImpl(tenantProperties.id(), Clock.systemUTC(), guard, codec, types, createType, updateType,
                compatibility, schemas, saveSchema, connectors, manageConnector, catalog, dmnDeployment, dmnParser,
                workflowDeployment, bpmnParser, workflowConfigs, saveWorkflowConfig, propose, journal);
    }
}
