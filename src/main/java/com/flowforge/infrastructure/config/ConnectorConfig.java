package com.flowforge.infrastructure.config;

import com.flowforge.domain.connector.port.in.CallbackConnectorRuntime;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.in.ExecuteConnectorUseCase;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.usecase.CallbackConnectorRuntimeImpl;
import com.flowforge.domain.connector.usecase.ExecuteConnectorUseCaseImpl;
import com.flowforge.domain.connector.usecase.ConnectorTriggerRuntimeImpl;
import com.flowforge.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConnectorProperties.class)
public class ConnectorConfig {

    @Bean
    ConnectorTriggerRuntime connectorTriggerRuntime(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository) {
        return new ConnectorTriggerRuntimeImpl(catalog, createDossier, getDossier, outboxRepository);
    }

    @Bean
    CallbackConnectorRuntime callbackConnectorRuntime(
            ConnectorCatalog catalog, WorkflowEnginePort workflowEnginePort) {
        return new CallbackConnectorRuntimeImpl(catalog, workflowEnginePort);
    }

    @Bean
    ManageConnectorInstanceUseCase manageConnectorInstanceUseCase(
            ConnectorInstanceRepository repository,
            ConnectorCatalog catalog,
            TenantAdminGuard tenantAdminGuard,
            ConnectorProperties properties) {
        return new ManageConnectorInstanceUseCaseImpl(
                repository,
                catalog,
                tenantAdminGuard,
                Map.of("kafka", properties.kafka().maxInstancesPerTenant()));
    }

    @Bean
    ExecuteConnectorUseCase executeConnectorUseCase(
            ConnectorInstanceRepository repository,
            ConnectorCatalog catalog) {
        return new ExecuteConnectorUseCaseImpl(repository, catalog);
    }

    @Bean
    HttpClient httpClient(ConnectorProperties properties) {
        // D-44 / SSRF: never follow redirects — a redirect target is never re-checked against
        // OutboundUrlGuard, so following one would silently bypass the pinned-IP validation
        // done before this client is invoked (HttpRestConnector). Explicit rather than relying
        // on the JDK's default (also NEVER) so this guarantee cannot regress unnoticed.
        return HttpClient.newBuilder()
                .connectTimeout(properties.http().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
