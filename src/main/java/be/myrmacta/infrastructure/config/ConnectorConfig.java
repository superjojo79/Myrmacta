package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.connector.port.in.CallbackConnectorRuntime;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.usecase.CallbackConnectorRuntimeImpl;
import be.myrmacta.domain.connector.usecase.ExecuteConnectorUseCaseImpl;
import be.myrmacta.domain.connector.usecase.ConnectorTriggerRuntimeImpl;
import be.myrmacta.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import java.net.http.HttpClient;
import java.util.Map;
import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.connector.usecase.AttachInboundFilesUseCaseImpl;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
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
