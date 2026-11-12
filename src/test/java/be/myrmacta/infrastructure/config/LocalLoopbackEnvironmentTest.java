package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * TODO §132 (D-LOOPBACK-LOCAL-ENV) : le profil {@code test} avec {@code APP_ENVIRONMENT=staging} ne doit pas
 * ouvrir la boucle locale. Équivalent du refus de démarrage suggéré par l'issue #73, sans casser la suite qui
 * démarre en {@code staging} ({@code McpDirectPublicationIntegrationTest}).
 */
class LocalLoopbackEnvironmentTest {

    private static OutboundUrlGuard guard(String appEnvironment, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        if (appEnvironment != null) {
            environment.setProperty("app.environment", appEnvironment);
        }
        if (profiles.length > 0) {
            environment.setActiveProfiles(profiles);
        }
        return new NotificationConfig().outboundUrlGuard(environment);
    }

    @Test
    void testProfile_withStaging_refusesLoopback() {
        OutboundUrlGuard guard = guard("staging", "test");

        assertThat(guard.isLocalLoopback("localhost")).isFalse();
        assertThat(guard.isLocalLoopback("127.0.0.1")).isFalse();
        assertThat(guard.isLocalLoopback("[::1]")).isFalse();
        assertThatThrownBy(() -> guard.assertSafe("http://127.0.0.1/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> guard.assertSafe("http://localhost:3143/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testProfile_withLocalEnvironment_allowsLoopback() {
        OutboundUrlGuard guard = guard("local", "test");

        assertThat(guard.isLocalLoopback("localhost")).isTrue();
        assertThat(guard.isLocalLoopback("127.0.0.1")).isTrue();
        assertThatCode(() -> guard.assertSafe("http://127.0.0.1:3143/hook")).doesNotThrowAnyException();
    }

    @Test
    void localProfile_withNormalizedEnvironment_allowsLoopback() {
        assertThat(guard(" Local ", "LOCAL").isLocalLoopback("localhost")).isTrue();
    }

    @Test
    void absentOrNonLocalEnvironment_refusesLoopbackEvenWithTestProfile() {
        assertThat(guard(null, "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("production", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("ci", "test").isLocalLoopback("localhost")).isFalse();
    }

    @Test
    void localEnvironment_withoutDevProfile_doesNotOpenLoopback() {
        assertThat(guard("local", "prod").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("local").isLocalLoopback("127.0.0.1")).isFalse();
    }
}
