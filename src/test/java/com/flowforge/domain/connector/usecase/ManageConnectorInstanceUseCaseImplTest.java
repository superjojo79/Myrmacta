package com.flowforge.domain.connector.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorInstanceNotFoundException;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManageConnectorInstanceUseCaseImplTest {

    private ConnectorInstanceRepository repository;
    private ConnectorDefinition definition;
    private ManageConnectorInstanceUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ConnectorInstanceRepository.class);
        ConnectorCatalog catalog = mock(ConnectorCatalog.class);
        definition = mock(ConnectorDefinition.class);
        when(catalog.get("http-rest")).thenReturn(definition);
        useCase = new ManageConnectorInstanceUseCaseImpl(repository, catalog);
    }

    @Test
    void create_validatesDefinitionAndPersistsInstance() {
        Map<String, Object> config = Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", "secret");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData created = useCase.create(new ManageConnectorInstanceUseCase.CreateCommand(
                "tenant_dev", "Claims webhook", "http-rest", true, config));

        assertThat(created.id()).isNotNull();
        assertThat(created.tenantId()).isEqualTo("tenant_dev");
        assertThat(created.name()).isEqualTo("Claims webhook");
        assertThat(created.connectorType()).isEqualTo("http-rest");
        assertThat(created.enabled()).isTrue();
        assertThat(created.config()).isEqualTo(config);
        verify(definition).validate(config);
        verify(repository).save(created);
    }

    @Test
    void update_mergesNullableFieldsWithExistingInstance() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData existing = new ConnectorInstanceData(
                id, "tenant_dev", "Old", "http-rest", false, Map.of("webhookSecret", "old"));
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData updated = useCase.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                id, "tenant_dev", "New", true, null, false));

        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.enabled()).isTrue();
        assertThat(updated.config()).isEqualTo(existing.config());
        verify(definition).validate(existing.config());
    }

    @Test
    void update_preservesExistingPasswordWhenMaskedValueIsSubmitted() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData existing = new ConnectorInstanceData(
                id,
                "tenant_dev",
                "Webhook",
                "http-rest",
                true,
                Map.of(
                        "webhookAuthType", "SHARED_SECRET_TOKEN",
                        "webhookSecret", "plain-secret",
                        "processKey", "oldProcess",
                        "dossierTypeCode", "CLAIM"));
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(definition.getConfigSchema()).thenReturn(List.of(new ConfigField(
                "webhookSecret", "Webhook secret", ConfigFieldType.PASSWORD, true, null, List.of(), null)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData updated = useCase.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                id,
                "tenant_dev",
                null,
                null,
                Map.of(
                        "webhookAuthType", "SHARED_SECRET_TOKEN",
                        "webhookSecret", "********",
                        "processKey", "newProcess",
                        "dossierTypeCode", "CLAIM"),
                false));

        assertThat(updated.config()).containsEntry("webhookSecret", "plain-secret");
        assertThat(updated.config()).containsEntry("processKey", "newProcess");
        verify(definition).validate(updated.config());
    }

    @Test
    void restore_loadsIncludingDeletedAndSavesEnabledInstance() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData deleted = new ConnectorInstanceData(
                id, "tenant_dev", "Deleted", "http-rest", false, Map.of("webhookSecret", "secret"));
        when(repository.findByIdAndTenantIdIncludingDeleted(id, "tenant_dev")).thenReturn(Optional.of(deleted));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData restored = useCase.restore(id, "tenant_dev");

        assertThat(restored.enabled()).isTrue();
        ArgumentCaptor<ConnectorInstanceData> saved = ArgumentCaptor.forClass(ConnectorInstanceData.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().enabled()).isTrue();
    }

    @Test
    void get_missingInstanceThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(id, "tenant_dev"))
                .isInstanceOf(ConnectorInstanceNotFoundException.class);
    }

    @Test
    void list_delegatesToRepository() {
        ConnectorInstanceData instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "Claims", "http-rest", true, Map.of());
        when(repository.pageByTenantId("tenant_dev", PageQuery.defaults()))
                .thenReturn(PageResult.of(List.of(instance), 0, 20, 1));

        assertThat(useCase.list("tenant_dev", PageQuery.defaults()).content()).containsExactly(instance);
    }
}
