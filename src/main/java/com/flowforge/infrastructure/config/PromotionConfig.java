package com.flowforge.infrastructure.config;

import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.promotion.port.in.ExportConfigurationUseCase;
import com.flowforge.domain.promotion.port.in.ImportConfigurationUseCase;
import com.flowforge.domain.promotion.port.out.ArtifactCodec;
import com.flowforge.domain.promotion.port.out.ConfigImportLogRepository;
import com.flowforge.domain.promotion.port.out.ConfigurationBundleCodec;
import com.flowforge.domain.promotion.usecase.ExportConfigurationUseCaseImpl;
import com.flowforge.domain.promotion.usecase.ImportConfigurationUseCaseImpl;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.infrastructure.promotion.JsonArtifactCodec;
import com.flowforge.infrastructure.promotion.ZipConfigurationBundleCodec;
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
