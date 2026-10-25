package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * S-STUB-4 à S-STUB-7, S-CONN-2, S-CONN-4 (beans) : sélection de l'adaptateur et garde du stub
 * (TODO §72, D-SIGNATURE-STUB-GUARD).
 */
class SignatureConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SignatureConfig.class)
            .withBean(PortalProperties.class, () -> new PortalProperties(false, null, null, null, null, null, null, 0))
            .withBean(TenantProperties.class, () -> new TenantProperties("tenant_test"))
            .withBean(SignatureRequestRepository.class, () -> mock(SignatureRequestRepository.class))
            .withBean(DocumentStoragePort.class, () -> mock(DocumentStoragePort.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultProviderNoneExposesNoSignaturePort() {
        // S-STUB-7, S-CONN-4
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(SignaturePort.class);
            assertThat(ctx.getBean(SignatureAvailabilityPort.class).providerActive()).isFalse();
        });
    }

    @Test
    void stubOutsideSafeProfilesWithoutFlagFailsStartupWithAnExplicitMessage() {
        // S-STUB-4 (aucun profil = production par défaut)
        runner.withPropertyValues("app.signature.provider=stub").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("allow-outside-local");
        });
        runner.withPropertyValues("app.signature.provider=stub")
                .withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void stubWithTheExplicitFlagStartsAndReportsItself() {
        // S-STUB-5
        runner.withPropertyValues("app.signature.provider=stub", "app.signature.stub.allow-outside-local=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(SignaturePort.class).code()).isEqualTo("stub");
                    assertThat(ctx.getBean(SignatureAvailabilityPort.class).providerActive()).isTrue();
                    assertThat(ctx.getBean(MeterRegistry.class)
                                    .get("myrmacta.signature.stub.active")
                                    .tag("outside_local", "true")
                                    .gauge()
                                    .value())
                            .isEqualTo(1.0);
                });
    }

    @Test
    void stubStartsWithoutFlagUnderLocalOrTestProfile() {
        // S-STUB-6
        for (String profile : new String[] {"local", "test"}) {
            runner.withPropertyValues("app.signature.provider=stub")
                    .withInitializer(c -> c.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed();
                        assertThat(ctx).hasSingleBean(SignaturePort.class);
                    });
        }
    }

    @Test
    void unknownProviderFailsStartupExplicitly() {
        // S-CONN-2
        runner.withPropertyValues("app.signature.provider=docusign").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("docusign");
        });
    }
}
