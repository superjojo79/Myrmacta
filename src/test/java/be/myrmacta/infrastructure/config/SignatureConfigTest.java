package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.infrastructure.signature.eid.EidTrustAnchors;
import be.myrmacta.infrastructure.signature.eid.SignedPdfVerifier;
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
        runner.withPropertyValues("app.signature.provider=universign").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("universign");
        });
    }

    @Test
    void docusignProviderExposesTheHostedAdapter() {
        runner.withBean(ConnectorInstanceRepository.class, () -> mock(ConnectorInstanceRepository.class))
                .withPropertyValues("app.signature.provider=docusign")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    SignaturePort port = ctx.getBean(SignaturePort.class);
                    assertThat(port.code()).isEqualTo("docusign");
                    assertThat(port.capabilities().hostedSigningUrl()).isTrue();
                    assertThat(port.capabilities().optionalSigners()).isFalse();
                });
    }

    @Test
    void eidProviderExposesTheUploadAdapterAndTheTrustAnchors() {
        // TODO §138 (D-EID-UPLOAD, D-EID-TRUST)
        runner.withBean(SignedUploadRepository.class, () -> mock(SignedUploadRepository.class))
                .withBean(SignedPdfVerifier.class, () -> mock(SignedPdfVerifier.class))
                .withPropertyValues("app.signature.provider=eid",
                        "app.signature.eid.trust-anchors-file=src/test/resources/eid/test-anchors.pem")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    SignaturePort port = ctx.getBean(SignaturePort.class);
                    assertThat(port.code()).isEqualTo("eid");
                    assertThat(port.capabilities().signedUpload()).isTrue();
                    assertThat(ctx.getBean(EidTrustAnchors.class).certificates()).hasSize(2);
                    assertThat(ctx.getBean(SignatureAvailabilityPort.class).maxSignedUploadBytes())
                            .isGreaterThan(ctx.getBean(SignatureAvailabilityPort.class).maxDocumentBytes());
                });
    }

    @Test
    void eidRevocationTransportHonoursTheOperatorSettingsAndNoTimestampAuthorityIsWiredByDefault() {
        // TODO §138 (D-EID-REVOCATION, D-EID-TIMESTAMP)
        runner.withBean(SignedUploadRepository.class, () -> mock(SignedUploadRepository.class))
                .withBean(SignedPdfVerifier.class, () -> mock(SignedPdfVerifier.class))
                .withPropertyValues("app.signature.provider=eid",
                        "app.signature.eid.trust-anchors-file=src/test/resources/eid/test-anchors.pem",
                        "app.signature.eid.revocation.timeout=4s",
                        "app.signature.eid.revocation.max-response-bytes=4096")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var transport = ctx.getBean(be.myrmacta.infrastructure.signature.eid.PinnedRevocationTransport.class);
                    assertThat(transport.totalTimeout()).isEqualTo(java.time.Duration.ofSeconds(4));
                    assertThat(transport.maxOcspBytes()).isEqualTo(4096);
                    assertThat(SignatureConfig.eidTimestamper(ctx.getBean(SignatureProperties.class),
                            ctx.getBean(EidTrustAnchors.class), transport)).isNull();
                });
        runner.withBean(SignedUploadRepository.class, () -> mock(SignedUploadRepository.class))
                .withBean(SignedPdfVerifier.class, () -> mock(SignedPdfVerifier.class))
                .withPropertyValues("app.signature.provider=eid",
                        "app.signature.eid.trust-anchors-file=src/test/resources/eid/test-anchors.pem",
                        "app.signature.eid.timestamp.url=https://tsa.example.test/tsp")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(SignatureConfig.eidTimestamper(ctx.getBean(SignatureProperties.class),
                            ctx.getBean(EidTrustAnchors.class),
                            ctx.getBean(be.myrmacta.infrastructure.signature.eid.PinnedRevocationTransport.class)))
                            .isNotNull();
                });
    }

    @Test
    void eidProviderRefusesToStartWithoutAUsableTrustAnchorsFile() {
        // D-EID-TRUST : sans fichier d'autorités, l'instance ne démarre pas
        var base = runner.withBean(SignedUploadRepository.class, () -> mock(SignedUploadRepository.class))
                .withBean(SignedPdfVerifier.class, () -> mock(SignedPdfVerifier.class));
        base.withPropertyValues("app.signature.provider=eid").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("trust-anchors-file");
        });
        base.withPropertyValues("app.signature.provider=eid",
                        "app.signature.eid.trust-anchors-file=src/test/resources/eid/absent.pem")
                .run(ctx -> assertThat(ctx).hasFailed());
        base.withPropertyValues("app.signature.provider=eid",
                        "app.signature.eid.trust-anchors-file=src/test/resources/eid/empty.pem")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
