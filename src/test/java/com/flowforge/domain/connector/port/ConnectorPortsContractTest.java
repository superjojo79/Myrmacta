package com.flowforge.domain.connector.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ExecuteConnectorUseCase;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.domain.connector.port.out.WebhookIdempotencyStore;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorPortsContractTest {

    @Test
    void repository_cipher_idempotency_and_useCasePorts_areCallable() {
        UUID id = UUID.randomUUID();
        var instance = new ConnectorInstanceData(id, "tenant_dev", "n", "http-rest", true, Map.of());

        ConnectorInstanceRepository repo = new ConnectorInstanceRepository() {
            @Override
            public ConnectorInstanceData save(ConnectorInstanceData data) {
                return data;
            }

            @Override
            public Optional<ConnectorInstanceData> findByIdAndTenantId(UUID instanceId, String tenantId) {
                return Optional.of(instance);
            }

            @Override
            public Optional<ConnectorInstanceData> findByIdAndTenantIdIncludingDeleted(
                    UUID instanceId, String tenantId) {
                return Optional.of(instance);
            }

            @Override
            public List<ConnectorInstanceData> listByTenantId(String tenantId) {
                return List.of(instance);
            }

            @Override
            public void softDelete(UUID instanceId, String tenantId) {}
        };

        ConnectorSecretCipher cipher = new ConnectorSecretCipher() {
            @Override
            public Map<String, Object> encryptPasswordFields(
                    String tenantId, String connectorType, Map<String, Object> config) {
                return config;
            }

            @Override
            public Map<String, Object> decryptPasswordFields(
                    String tenantId, String connectorType, Map<String, Object> config) {
                return config;
            }

            @Override
            public String encrypt(String tenantId, String plaintext) {
                return "enc:v1:" + plaintext;
            }

            @Override
            public String decrypt(String tenantId, String ciphertext) {
                return ciphertext.startsWith("enc:v1:") ? ciphertext.substring("enc:v1:".length()) : ciphertext;
            }
        };

        WebhookIdempotencyStore store = new WebhookIdempotencyStore() {
            @Override
            public Optional<Integer> beginOrGetCompleted(UUID instanceId, String idempotencyKey) {
                return Optional.empty();
            }

            @Override
            public void complete(UUID instanceId, String idempotencyKey, int responseStatus) {}

            @Override
            public void release(UUID instanceId, String idempotencyKey) {}

            @Override
            public Optional<Integer> findResponseStatus(UUID instanceId, String idempotencyKey) {
                return Optional.empty();
            }
        };

        ManageConnectorInstanceUseCase manage = new ManageConnectorInstanceUseCase() {
            @Override
            public void assertAuthorized(AccessContext.User actor) {}

            @Override
            public ConnectorInstanceData create(CreateCommand command, AccessContext.User actor) {
                return instance;
            }

            @Override
            public ConnectorInstanceData update(UpdateCommand command, AccessContext.User actor) {
                return instance;
            }

            @Override
            public ConnectorInstanceData get(UUID instanceId, String tenantId, AccessContext.User actor) {
                return instance;
            }

            @Override
            public PageResult<ConnectorInstanceData> list(
                    String tenantId, PageQuery query, AccessContext.User actor) {
                return PageResult.of(List.of(instance), query.page(), query.size(), 1);
            }

            @Override
            public void softDelete(UUID instanceId, String tenantId, AccessContext.User actor) {}

            @Override
            public ConnectorInstanceData restore(UUID instanceId, String tenantId, AccessContext.User actor) {
                return instance;
            }
        };

        ExecuteConnectorUseCase execute = (instanceId, tenantId, input) -> Map.of("signaled", true);

        assertThat(repo.findByIdAndTenantId(id, "tenant_dev")).contains(instance);
        assertThat(cipher.encryptPasswordFields("tenant_dev", "http-rest", Map.of("webhookSecret", "x")))
                .containsEntry("webhookSecret", "x");
        assertThat(store.findResponseStatus(id, "k")).isEmpty();
        assertThat(manage.create(
                        new ManageConnectorInstanceUseCase.CreateCommand("tenant_dev", "n", "http-rest", true, Map.of()),
                        AccessContext.user("svc", Set.of("API_SERVICE"))))
                .isEqualTo(instance);
        assertThat(execute.execute(id, "tenant_dev", Map.of())).containsEntry("signaled", true);
    }
}
