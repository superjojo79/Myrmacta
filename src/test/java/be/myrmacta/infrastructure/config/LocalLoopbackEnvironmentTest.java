package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.notification.port.out.UserNotificationRepository;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
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
        assertThat(guard.isLocalLoopback("::1")).isTrue();
        assertThat(guard.isLocalLoopback("[::1]")).isTrue();
        assertThatCode(() -> guard.assertSafe("http://127.0.0.1:3143/hook")).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertSafe("http://[::1]/hook")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertSafe("http://hooks.example.com/notify"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OutboundUrlGuard.LOCAL_LOOPBACK_CONDITION);
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

    @Test
    void emptyEnvironment_andSurroundingSpacesOnProfiles_followTheSameRule() {
        assertThat(guard("", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("   ", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("local", " test ").isLocalLoopback("localhost")).isTrue();
        assertThat(guard("local", " LOCAL ").isLocalLoopback("[::1]")).isTrue();
        assertThat(OutboundUrlGuard.allowsLocalLoopback("local", "  ")).isFalse();
    }

    @Test
    void springBean_honorsRelaxedAppEnvironmentProperty() {
        springGuard("staging", "test", guard -> {
            assertThat(guard.isLocalLoopback("localhost")).isFalse();
            assertThat(guard.isLocalLoopback("[::1]")).isFalse();
        });
        springGuard("local", " test ", guard -> {
            assertThat(guard.isLocalLoopback("localhost")).isTrue();
            assertThat(guard.isLocalLoopback("[::1]")).isTrue();
            assertThatCode(() -> guard.assertSafe("http://[::1]/hook")).doesNotThrowAnyException();
        });
        springGuard("", "test", guard -> assertThat(guard.isLocalLoopback("localhost")).isFalse());
    }

    private static void springGuard(String appEnvironment, String profile, Consumer<OutboundUrlGuard> assertions) {
        new ApplicationContextRunner()
                .withUserConfiguration(NotificationConfig.class)
                .withBean(NotificationOutboxRepository.class, () -> mock(NotificationOutboxRepository.class))
                .withBean(NotificationWebhookRepository.class, () -> mock(NotificationWebhookRepository.class))
                .withBean(DossierTypeRepository.class, () -> mock(DossierTypeRepository.class))
                .withBean(UserNotificationRepository.class, () -> mock(UserNotificationRepository.class))
                .withBean(DossierElementRepository.class, () -> mock(DossierElementRepository.class))
                .withBean(DocumentStoragePort.class, () -> mock(DocumentStoragePort.class))
                .withBean(TenantProperties.class, () -> new TenantProperties("tenant_test"))
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles(profile);
                    context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                            "relaxed-app-environment", Map.of("APP_ENVIRONMENT", appEnvironment)));
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertions.accept(context.getBean(OutboundUrlGuard.class));
                });
    }
}
