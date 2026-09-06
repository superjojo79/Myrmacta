package com.flowforge.infrastructure.connector.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.infrastructure.config.ConnectorProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EnvMasterKeyCipherTest {

    private static final String MASTER_KEY_B64 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String CONNECTOR_TYPE = "http-rest";

    private InMemoryDekStore dekStore;
    private ConnectorCatalog catalog;
    private EnvMasterKeyCipher cipher;

    @BeforeEach
    void setUp() {
        dekStore = new InMemoryDekStore();
        catalog = Mockito.mock(ConnectorCatalog.class);
        var props = new ConnectorProperties(
                new ConnectorProperties.Crypto(MASTER_KEY_B64),
                new ConnectorProperties.Webhook(1_048_576L, 120, Duration.ofHours(24)),
                null,
                null);
        cipher = new EnvMasterKeyCipher(props, dekStore, catalog);
    }

    @Test
    void encryptDecrypt_roundTripsPasswordFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("webhookAuthType", "HMAC_SHA256");
        config.put("webhookSecret", "super-secret");
        config.put("url", "https://example.com");

        Map<String, Object> encrypted = cipher.encryptPasswordFields("tenant_dev", CONNECTOR_TYPE, config);
        assertThat(encrypted.get("webhookAuthType")).isEqualTo("HMAC_SHA256");
        assertThat(encrypted.get("url")).isEqualTo("https://example.com");
        assertThat(encrypted.get("webhookSecret").toString()).startsWith("enc:v1:");
        assertThat(encrypted.get("webhookSecret")).isNotEqualTo("super-secret");

        Map<String, Object> decrypted = cipher.decryptPasswordFields("tenant_dev", CONNECTOR_TYPE, encrypted);
        assertThat(decrypted.get("webhookSecret")).isEqualTo("super-secret");
        assertThat(decrypted.get("url")).isEqualTo("https://example.com");
    }

    @Test
    void encryptDecrypt_roundTripsSchemaDeclaredPasswordFieldRegardlessOfName() {
        // authHeaderValue matches no name heuristic (adversarial review follow-up) — only the
        // connector type's declared PASSWORD schema field catches it.
        ConnectorDefinition definition = Mockito.mock(ConnectorDefinition.class);
        Mockito.when(definition.getConfigSchema()).thenReturn(List.of(
                new ConfigField("authHeaderValue", "Auth header", ConfigFieldType.PASSWORD, false, null, List.of(), null)));
        Mockito.when(catalog.get(CONNECTOR_TYPE)).thenReturn(definition);
        Mockito.when(catalog.isPasswordField(CONNECTOR_TYPE, "authHeaderValue")).thenCallRealMethod();

        Map<String, Object> config = Map.of("authHeaderValue", "Bearer super-secret-token", "url", "https://example.com");

        Map<String, Object> encrypted = cipher.encryptPasswordFields("tenant_dev", CONNECTOR_TYPE, config);
        assertThat(encrypted.get("authHeaderValue").toString()).startsWith("enc:v1:");
        assertThat(encrypted.get("url")).isEqualTo("https://example.com");

        Map<String, Object> decrypted = cipher.decryptPasswordFields("tenant_dev", CONNECTOR_TYPE, encrypted);
        assertThat(decrypted.get("authHeaderValue")).isEqualTo("Bearer super-secret-token");
    }

    @Test
    void encrypt_createsDekForTenantOnce() {
        cipher.encryptPasswordFields("tenant_a", CONNECTOR_TYPE, Map.of("webhookSecret", "a"));
        cipher.encryptPasswordFields("tenant_a", CONNECTOR_TYPE, Map.of("clientSecret", "b"));
        assertThat(dekStore.count("tenant_a")).isEqualTo(1);
        assertThat(dekStore.count("tenant_b")).isEqualTo(0);
    }

    @Test
    void nonPasswordFields_unchanged() {
        Map<String, Object> config = Map.of("processKey", "smokeProcess", "enabledFlag", "true");
        assertThat(cipher.encryptPasswordFields("tenant_dev", CONNECTOR_TYPE, config)).isEqualTo(config);
    }

    @Test
    void missingMasterKey_rejected() {
        var props = new ConnectorProperties(
                new ConnectorProperties.Crypto(null),
                new ConnectorProperties.Webhook(1_048_576L, 120, Duration.ofHours(24)),
                null,
                null);
        var broken = new EnvMasterKeyCipher(props, dekStore, catalog);
        assertThatThrownBy(() -> broken.encryptPasswordFields("t", CONNECTOR_TYPE, Map.of("webhookSecret", "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master-key");
    }

    @Test
    void encryptDecrypt_stringRoundTrip() {
        String ciphertext = cipher.encrypt("tenant_dev", "webhook-hmac-secret");
        assertThat(ciphertext).startsWith("enc:v1:");
        assertThat(ciphertext).doesNotContain("webhook-hmac-secret");
        assertThat(cipher.decrypt("tenant_dev", ciphertext)).isEqualTo("webhook-hmac-secret");
    }

    @Test
    void encrypt_nullOrBlank_throwsIllegalArgument() {
        assertThatThrownBy(() -> cipher.encrypt("tenant_dev", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.encrypt("tenant_dev", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decrypt_withoutPrefix_throwsIllegalArgument() {
        assertThatThrownBy(() -> cipher.decrypt("tenant_dev", "not-encrypted"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enc:v1:");
    }

    /** Minimal DEK store for unit tests — production uses JPA. */
    static final class InMemoryDekStore implements TenantDekStore {
        private final Map<String, String> wrappedByTenant = new ConcurrentHashMap<>();

        @Override
        public Optional<String> findWrappedDek(String tenantId) {
            return Optional.ofNullable(wrappedByTenant.get(tenantId));
        }

        @Override
        public void saveWrappedDek(String tenantId, String wrappedDek, int keyVersion) {
            wrappedByTenant.putIfAbsent(tenantId, wrappedDek);
        }

        int count(String tenantId) {
            return wrappedByTenant.containsKey(tenantId) ? 1 : 0;
        }
    }
}
