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
import com.flowforge.domain.connector.model.ConnectorInstanceQuotaExceededException;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManageConnectorInstanceUseCaseImplTest {

    private static final AccessContext.User TENANT_ADMIN = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User API_SERVICE = AccessContext.user("svc", Set.of("API_SERVICE"));
    private static final AccessContext.User NO_ROLE = AccessContext.user("nobody", Set.of("DOSSIER_MANAGER"));

    private ConnectorInstanceRepository repository;
    private ConnectorCatalog catalog;
    private ConnectorDefinition definition;
    private ManageConnectorInstanceUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ConnectorInstanceRepository.class);
        catalog = mock(ConnectorCatalog.class);
        definition = mock(ConnectorDefinition.class);
        when(catalog.get("http-rest")).thenReturn(definition);
        when(definition.getConfigSchema())
                .thenReturn(List.of(
                        new ConfigField("webhookAuthType", "auth", ConfigFieldType.SELECT, true, null, List.of(), null),
                        new ConfigField(
                                "webhookSecret", "secret", ConfigFieldType.PASSWORD, true, null, List.of(), null),
                        new ConfigField("mapping", "mapping", ConfigFieldType.STRING, false, null, List.of(), null)));
        useCase = new ManageConnectorInstanceUseCaseImpl(repository, catalog, new TenantAdminGuard());
    }

    @Test
    void create_validatesDefinitionAndPersistsInstance() {
        Map<String, Object> config = Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", "secret");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData created = useCase.create(new ManageConnectorInstanceUseCase.CreateCommand(
                "tenant_dev", "Claims webhook", "http-rest", true, config), TENANT_ADMIN);

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
    void create_unknownConfigKey_isRejected() {
        Map<String, Object> config = Map.of("backupToken", "x");

        assertThatThrownBy(() -> useCase.create(new ManageConnectorInstanceUseCase.CreateCommand(
                        "tenant_dev", "Claims webhook", "http-rest", true, config), TENANT_ADMIN))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("backupToken");
    }

    @Test
    void create_mappingConfigKey_isAccepted() {
        Map<String, Object> config = Map.of("mapping", Map.of("fields", Map.of("a", "$.a")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData created = useCase.create(new ManageConnectorInstanceUseCase.CreateCommand(
                "tenant_dev", "Claims webhook", "http-rest", true, config), TENANT_ADMIN);

        assertThat(created.config()).containsKey("mapping");
    }

    @Test
    void assertAuthorized_rejectsActorWithoutRequiredRole() {
        assertThatThrownBy(() -> useCase.assertAuthorized(NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertAuthorized_acceptsTenantAdminAndApiService() {
        useCase.assertAuthorized(TENANT_ADMIN);
        useCase.assertAuthorized(API_SERVICE);
    }

    @Test
    void create_apiService_isAllowed() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(useCase.create(new ManageConnectorInstanceUseCase.CreateCommand(
                        "tenant_dev", "Claims webhook", "http-rest", true, Map.of()), API_SERVICE))
                .isNotNull();
    }

    @Test
    void create_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.create(new ManageConnectorInstanceUseCase.CreateCommand(
                        "tenant_dev", "Claims webhook", "http-rest", true, Map.of()), NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_mergesNullableFieldsWithExistingInstance() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData existing = new ConnectorInstanceData(
                id, "tenant_dev", "Old", "http-rest", false, Map.of("webhookSecret", "old"));
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData updated = useCase.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                id, "tenant_dev", "New", true, null, false), TENANT_ADMIN);

        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.enabled()).isTrue();
        assertThat(updated.config()).isEqualTo(existing.config());
        verify(definition).validate(existing.config());
    }

    @Test
    void update_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                        UUID.randomUUID(), "tenant_dev", "New", true, null, false), NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
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
        when(definition.getConfigSchema()).thenReturn(List.of(
                new ConfigField(
                        "webhookSecret", "Webhook secret", ConfigFieldType.PASSWORD, true, null, List.of(), null),
                new ConfigField(
                        "webhookAuthType", "auth", ConfigFieldType.SELECT, true, null, List.of(), null),
                new ConfigField("processKey", "pk", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("dossierTypeCode", "dt", ConfigFieldType.STRING, true, null, List.of(), null)));
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
                false), TENANT_ADMIN);

        assertThat(updated.config()).containsEntry("webhookSecret", "plain-secret");
        assertThat(updated.config()).containsEntry("processKey", "newProcess");
        verify(definition).validate(updated.config());
    }

    @Test
    void update_preservesSchemaDeclaredPasswordFieldRegardlessOfName() {
        // authHeaderValue matches no name heuristic (adversarial review follow-up) — only the
        // connector type's declared PASSWORD schema field catches it.
        UUID id = UUID.randomUUID();
        ConnectorInstanceData existing = new ConnectorInstanceData(
                id, "tenant_dev", "Webhook", "http-rest", true,
                Map.of("authHeaderValue", "Bearer plain-token", "url", "https://old.example.com"));
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(definition.getConfigSchema()).thenReturn(List.of(
                new ConfigField(
                        "authHeaderValue", "Auth header", ConfigFieldType.PASSWORD, false, null, List.of(), null),
                new ConfigField("url", "url", ConfigFieldType.STRING, false, null, List.of(), null)));
        when(catalog.isPasswordField("http-rest", "authHeaderValue")).thenCallRealMethod();
        when(catalog.isPasswordField("http-rest", "url")).thenCallRealMethod();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData updated = useCase.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                id, "tenant_dev", null, null,
                Map.of("authHeaderValue", "********", "url", "https://new.example.com"),
                false), TENANT_ADMIN);

        assertThat(updated.config()).containsEntry("authHeaderValue", "Bearer plain-token");
        assertThat(updated.config()).containsEntry("url", "https://new.example.com");
    }

    @Test
    void restore_loadsIncludingDeletedAndSavesEnabledInstance() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData deleted = new ConnectorInstanceData(
                id, "tenant_dev", "Deleted", "http-rest", false, Map.of("webhookSecret", "secret"));
        when(repository.findByIdAndTenantIdIncludingDeleted(id, "tenant_dev")).thenReturn(Optional.of(deleted));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData restored = useCase.restore(id, "tenant_dev", TENANT_ADMIN);

        assertThat(restored.enabled()).isTrue();
        ArgumentCaptor<ConnectorInstanceData> saved = ArgumentCaptor.forClass(ConnectorInstanceData.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().enabled()).isTrue();
    }

    @Test
    void restore_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.restore(UUID.randomUUID(), "tenant_dev", NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void get_missingInstanceThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(id, "tenant_dev", TENANT_ADMIN))
                .isInstanceOf(ConnectorInstanceNotFoundException.class);
    }

    @Test
    void get_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.get(UUID.randomUUID(), "tenant_dev", NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void list_delegatesToRepository() {
        ConnectorInstanceData instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "Claims", "http-rest", true, Map.of());
        when(repository.pageByTenantId("tenant_dev", PageQuery.defaults()))
                .thenReturn(PageResult.of(List.of(instance), 0, 20, 1));

        assertThat(useCase.list("tenant_dev", PageQuery.defaults(), TENANT_ADMIN).content())
                .containsExactly(instance);
    }

    @Test
    void list_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.list("tenant_dev", PageQuery.defaults(), NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void softDelete_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.softDelete(UUID.randomUUID(), "tenant_dev", NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- Quota d'instances activées par type (revue adversariale, connecteurs-cible lot B1) ---

    private ManageConnectorInstanceUseCase quotaLimitedUseCase(int maxKafkaInstances) {
        ConnectorDefinition kafkaDefinition = mock(ConnectorDefinition.class);
        when(catalog.get("kafka")).thenReturn(kafkaDefinition);
        when(kafkaDefinition.getConfigSchema()).thenReturn(List.of());
        return new ManageConnectorInstanceUseCaseImpl(
                repository, catalog, new TenantAdminGuard(), Map.of("kafka", maxKafkaInstances));
    }

    private static ConnectorInstanceData enabledKafkaInstance() {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "Kafka " + UUID.randomUUID(), "kafka", true, Map.of());
    }

    @Test
    void create_enabledInstance_withinQuota_succeeds() {
        ManageConnectorInstanceUseCase limited = quotaLimitedUseCase(2);
        when(repository.listByTenantId("tenant_dev")).thenReturn(List.of(enabledKafkaInstance()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData created = limited.create(
                new ManageConnectorInstanceUseCase.CreateCommand("tenant_dev", "Kafka 2", "kafka", true, Map.of()),
                TENANT_ADMIN);

        assertThat(created.enabled()).isTrue();
    }

    @Test
    void create_enabledInstance_exceedingQuota_isRejected() {
        ManageConnectorInstanceUseCase limited = quotaLimitedUseCase(1);
        when(repository.listByTenantId("tenant_dev")).thenReturn(List.of(enabledKafkaInstance()));

        assertThatThrownBy(() -> limited.create(
                        new ManageConnectorInstanceUseCase.CreateCommand(
                                "tenant_dev", "Kafka 2", "kafka", true, Map.of()),
                        TENANT_ADMIN))
                .isInstanceOf(ConnectorInstanceQuotaExceededException.class);
    }

    @Test
    void create_disabledInstance_neverCheckedAgainstQuota_evenAtLimit() {
        ManageConnectorInstanceUseCase limited = quotaLimitedUseCase(1);
        when(repository.listByTenantId("tenant_dev")).thenReturn(List.of(enabledKafkaInstance()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData created = limited.create(
                new ManageConnectorInstanceUseCase.CreateCommand("tenant_dev", "Kafka 2", "kafka", false, Map.of()),
                TENANT_ADMIN);

        assertThat(created.enabled()).isFalse();
    }

    @Test
    void create_typeWithoutConfiguredQuota_isUnaffectedByAnyLimit() {
        ManageConnectorInstanceUseCase limited = quotaLimitedUseCase(1);
        // http-rest has no entry in the quota map (only "kafka" does) — never checked.
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> config = Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", "secret");

        ConnectorInstanceData created = limited.create(
                new ManageConnectorInstanceUseCase.CreateCommand("tenant_dev", "Webhook", "http-rest", true, config),
                TENANT_ADMIN);

        assertThat(created.enabled()).isTrue();
        verify(repository, org.mockito.Mockito.never()).listByTenantId(any());
    }

    @Test
    void update_reEnablingInstance_exceedingQuota_isRejected() {
        ManageConnectorInstanceUseCase limited = quotaLimitedUseCase(1);
        UUID id = UUID.randomUUID();
        ConnectorInstanceData existing =
                new ConnectorInstanceData(id, "tenant_dev", "Kafka disabled", "kafka", false, Map.of());
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(repository.listByTenantId("tenant_dev")).thenReturn(List.of(enabledKafkaInstance()));

        assertThatThrownBy(() -> limited.update(
                        new ManageConnectorInstanceUseCase.UpdateCommand(
                                id, "tenant_dev", null, true, null, false),
                        TENANT_ADMIN))
                .isInstanceOf(ConnectorInstanceQuotaExceededException.class);
    }

    @Test
    void update_alreadyEnabledInstance_notRecheckedAgainstQuota() {
        // Un changement (ex. renommage) sur une instance déjà activée ne doit pas être bloqué
        // même si le tenant est par ailleurs au-dessus de la limite (ex. après son abaissement).
        ManageConnectorInstanceUseCase limited = quotaLimitedUseCase(1);
        UUID id = UUID.randomUUID();
        ConnectorInstanceData existing = new ConnectorInstanceData(id, "tenant_dev", "Kafka", "kafka", true, Map.of());
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorInstanceData updated = limited.update(
                new ManageConnectorInstanceUseCase.UpdateCommand(id, "tenant_dev", "Renamed", true, null, false),
                TENANT_ADMIN);

        assertThat(updated.name()).isEqualTo("Renamed");
        verify(repository, org.mockito.Mockito.never()).listByTenantId(any());
    }

    @Test
    void restore_reEnablingInstance_exceedingQuota_isRejected() {
        ManageConnectorInstanceUseCase limited = quotaLimitedUseCase(1);
        UUID id = UUID.randomUUID();
        ConnectorInstanceData deleted =
                new ConnectorInstanceData(id, "tenant_dev", "Kafka deleted", "kafka", false, Map.of());
        when(repository.findByIdAndTenantIdIncludingDeleted(id, "tenant_dev")).thenReturn(Optional.of(deleted));
        when(repository.listByTenantId("tenant_dev")).thenReturn(List.of(enabledKafkaInstance()));

        assertThatThrownBy(() -> limited.restore(id, "tenant_dev", TENANT_ADMIN))
                .isInstanceOf(ConnectorInstanceQuotaExceededException.class);
    }
}
