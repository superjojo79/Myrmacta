package com.flowforge.infrastructure.connector.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.infrastructure.config.ConnectorProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvMasterKeyCipherTest {

    private static final String MASTER_KEY_B64 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private InMemoryDekStore dekStore;
    private EnvMasterKeyCipher cipher;

    @BeforeEach
    void setUp() {
        dekStore = new InMemoryDekStore();
        var props = new ConnectorProperties(
                new ConnectorProperties.Crypto(MASTER_KEY_B64),
                new ConnectorProperties.Webhook(1_048_576L, 120, Duration.ofHours(24)));
        cipher = new EnvMasterKeyCipher(props, dekStore);
    }

    @Test
    void encryptDecrypt_roundTripsPasswordFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("webhookAuthType", "HMAC_SHA256");
        config.put("webhookSecret", "super-secret");
        config.put("url", "https://example.com");

        Map<String, Object> encrypted = cipher.encryptPasswordFields("tenant_dev", config);
        assertThat(encrypted.get("webhookAuthType")).isEqualTo("HMAC_SHA256");
        assertThat(encrypted.get("url")).isEqualTo("https://example.com");
        assertThat(encrypted.get("webhookSecret").toString()).startsWith("enc:v1:");
        assertThat(encrypted.get("webhookSecret")).isNotEqualTo("super-secret");

        Map<String, Object> decrypted = cipher.decryptPasswordFields("tenant_dev", encrypted);
        assertThat(decrypted.get("webhookSecret")).isEqualTo("super-secret");
        assertThat(decrypted.get("url")).isEqualTo("https://example.com");
    }

    @Test
    void encrypt_createsDekForTenantOnce() {
        cipher.encryptPasswordFields("tenant_a", Map.of("webhookSecret", "a"));
        cipher.encryptPasswordFields("tenant_a", Map.of("clientSecret", "b"));
        assertThat(dekStore.count("tenant_a")).isEqualTo(1);
        assertThat(dekStore.count("tenant_b")).isEqualTo(0);
    }

    @Test
    void nonPasswordFields_unchanged() {
        Map<String, Object> config = Map.of("processKey", "smokeProcess", "enabledFlag", "true");
        assertThat(cipher.encryptPasswordFields("tenant_dev", config)).isEqualTo(config);
    }

    @Test
    void missingMasterKey_rejected() {
        var props = new ConnectorProperties(
                new ConnectorProperties.Crypto(null),
                new ConnectorProperties.Webhook(1_048_576L, 120, Duration.ofHours(24)));
        var broken = new EnvMasterKeyCipher(props, dekStore);
        assertThatThrownBy(() -> broken.encryptPasswordFields("t", Map.of("webhookSecret", "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master-key");
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
