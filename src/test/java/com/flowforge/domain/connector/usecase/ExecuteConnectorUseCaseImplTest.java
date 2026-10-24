package com.flowforge.domain.connector.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.connector.model.ConnectorCallContext;
import com.flowforge.domain.connector.port.in.ExecuteConnectorUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExecuteConnectorUseCaseImplTest {

    private ConnectorInstanceRepository repository;
    private ConnectorCatalog catalog;
    private ExecuteConnectorUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ConnectorInstanceRepository.class);
        catalog = mock(ConnectorCatalog.class);
        useCase = new ExecuteConnectorUseCaseImpl(repository, catalog);
    }

    @Test
    void execute_delegatesToServiceConnector() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData instance = new ConnectorInstanceData(
                id, "tenant_dev", "Service", "service-test", true, Map.of());
        ConnectorDefinition definition =
                mock(ConnectorDefinition.class, Mockito.withSettings().extraInterfaces(ServiceConnector.class));
        ServiceConnector service = (ServiceConnector) definition;
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(instance));
        when(catalog.get("service-test")).thenReturn(definition);
        when(definition.getCapability()).thenReturn(ConnectorCapability.SERVICE);
        when(service.execute(instance, Map.of("input", "value"), ConnectorCallContext.direct()))
                .thenReturn(Map.of("ok", true));

        assertThat(useCase.execute(id, "tenant_dev", Map.of("input", "value")))
                .containsEntry("ok", true);
    }

    @Test
    void execute_rejectsTriggerOnlyConnector() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData instance = new ConnectorInstanceData(
                id, "tenant_dev", "Webhook", "http-rest", true, Map.of());
        ConnectorDefinition definition = mock(ConnectorDefinition.class);
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(instance));
        when(catalog.get("http-rest")).thenReturn(definition);
        when(definition.getCapability()).thenReturn(ConnectorCapability.TRIGGER);

        assertThatThrownBy(() -> useCase.execute(id, "tenant_dev", Map.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support SERVICE");
    }

    @Test
    void execute_rejectsDisabledConnector() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData instance = new ConnectorInstanceData(
                id, "tenant_dev", "Disabled", "service-test", false, Map.of());
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> useCase.execute(id, "tenant_dev", Map.of()))
                .isInstanceOf(com.flowforge.domain.connector.model.ConnectorInstanceNotFoundException.class);
    }

    @Test
    void execute_rejectsMissingConnector() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "tenant_dev", Map.of()))
                .isInstanceOf(com.flowforge.domain.connector.model.ConnectorInstanceNotFoundException.class);
    }
}
