package com.flowforge.infrastructure.config;

import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.in.ExecuteConnectorUseCase;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.usecase.ExecuteConnectorUseCaseImpl;
import com.flowforge.domain.connector.usecase.ConnectorTriggerRuntimeImpl;
import com.flowforge.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import java.net.http.HttpClient;
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
    ManageConnectorInstanceUseCase manageConnectorInstanceUseCase(
            ConnectorInstanceRepository repository,
            ConnectorCatalog catalog) {
        return new ManageConnectorInstanceUseCaseImpl(repository, catalog);
    }

    @Bean
    ExecuteConnectorUseCase executeConnectorUseCase(
            ConnectorInstanceRepository repository,
            ConnectorCatalog catalog) {
        return new ExecuteConnectorUseCaseImpl(repository, catalog);
    }

    @Bean
    HttpClient httpClient(ConnectorProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.http().connectTimeout())
                .build();
    }
}
