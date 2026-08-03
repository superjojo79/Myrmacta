package com.flowforge.infrastructure.config;

import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.usecase.ConnectorTriggerRuntimeImpl;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
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
}
