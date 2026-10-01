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
import com.flowforge.domain.connector.port.in.AttachInboundFilesUseCase;
import com.flowforge.domain.connector.usecase.AttachInboundFilesUseCaseImpl;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ConnectorProperties.class, InboundConnectorProperties.class})
public class ConnectorConfig {

    @Bean
    ConnectorTriggerRuntime connectorTriggerRuntime(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository,
            AttachInboundFilesUseCase attachInboundFiles) {
        return new ConnectorTriggerRuntimeImpl(catalog, createDossier, getDossier, outboxRepository, attachInboundFiles);
    }

    /** TODO §87 (D3c) — pièces jointes d'un e-mail entrant : mêmes contrôles que l'upload interne (types, contenu, antivirus). */
    @Bean
    AttachInboundFilesUseCase attachInboundFilesUseCase(
            TenantProperties tenantProperties,
            AttachmentUploadProperties uploadProperties,
            InboundConnectorProperties inbound,
            AttachmentScanPort scanPort,
            DocumentStoragePort storagePort,
            AddDossierElementUseCase addElementUseCase) {
        return new AttachInboundFilesUseCaseImpl(tenantProperties.id(), uploadProperties::isAllowed, 20, inbound.maxAttachmentBytes(),
                scanPort, storagePort, addElementUseCase);
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
