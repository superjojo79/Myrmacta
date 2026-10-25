package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.DeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.in.ManageDraftsUseCase;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.ConfigPublicationRepository;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.domain.workflow.port.out.PublicationPolicy;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.domain.workflow.usecase.ManageDraftsUseCaseImpl;
import be.myrmacta.domain.workflow.usecase.ProposeDraftUseCaseImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §82 (D6) — brouillons, publication et gouvernance des écritures de configuration venant du MCP. */
@Configuration
@EnableConfigurationProperties({McpPublicationProperties.class, DraftRetentionProperties.class})
class McpGovernanceConfig {

    @Bean
    PublicationPolicy publicationPolicy(McpPublicationProperties properties) {
        return properties::directAllowed;
    }

    @Bean
    McpGovernanceStartupGuard mcpGovernanceStartupGuard(
            @Value("${app.environment:production}") String environment,
            McpPublicationProperties publication,
            M2mSecurityProperties m2m) {
        return new McpGovernanceStartupGuard(environment, publication, m2m);
    }

    @Bean
    ProposeDraftUseCase proposeDraftUseCase(
            TenantProperties tenantProperties,
            TenantAdminGuard guard,
            BpmnDraftRepository bpmnDrafts,
            DmnDraftRepository dmnDrafts,
            BpmnParserPort bpmnParser,
            DmnParserPort dmnParser,
            DeployBpmnProcessUseCase deployBpmn,
            WorkflowTypeConfigRepository configRepository,
            DmnDeploymentPort dmnDeployment) {
        return new ProposeDraftUseCaseImpl(tenantProperties.id(), guard, bpmnDrafts, dmnDrafts, bpmnParser, dmnParser,
                deployBpmn, configRepository, dmnDeployment);
    }

    @Bean
    ManageDraftsUseCase manageDraftsUseCase(
            TenantProperties tenantProperties,
            TenantAdminGuard guard,
            BpmnDraftRepository bpmnDrafts,
            DmnDraftRepository dmnDrafts,
            DeployBpmnProcessUseCase deployBpmn,
            DeployDecisionTableUseCase deployDmn,
            SaveWorkflowTypeConfigUseCase saveConfig,
            WorkflowTypeConfigRepository configRepository,
            WorkflowDeploymentPort workflowDeployment,
            DmnDeploymentPort dmnDeployment,
            ConfigPublicationRepository publications,
            PublicationPolicy policy) {
        return new ManageDraftsUseCaseImpl(tenantProperties.id(), guard, bpmnDrafts, dmnDrafts, deployBpmn, deployDmn,
                saveConfig, configRepository, workflowDeployment, dmnDeployment, publications, policy);
    }
}
