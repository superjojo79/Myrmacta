package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.infrastructure.signature.DocusignSignatureAdapter;
import be.myrmacta.infrastructure.signature.SignatureStubGuard;
import be.myrmacta.infrastructure.signature.StubSignatureAdapter;
import be.myrmacta.infrastructure.signature.YousignSignatureAdapter;
import be.myrmacta.infrastructure.signature.eid.DssDocumentTimestamper;
import be.myrmacta.infrastructure.signature.eid.EidSignatureAdapter;
import be.myrmacta.infrastructure.signature.eid.EidTrustAnchors;
import be.myrmacta.infrastructure.signature.eid.PinnedRevocationTransport;
import be.myrmacta.infrastructure.signature.eid.SignedPdfVerifier;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §72 (signature-electronique) — câblage (domaine sans annotation Spring). */
@Configuration
@EnableConfigurationProperties(SignatureProperties.class)
public class SignatureConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureConfig.class);

    /**
     * Adaptateur de simulation, présent seulement si {@code app.signature.provider=stub}
     * (D-SIGNATURE-STUB-BEHAVIOR). La garde de profil (D-SIGNATURE-STUB-GUARD) s'exécute à la création
     * du bean : un refus fait échouer le démarrage du contexte avec un message explicite.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.signature", name = "provider", havingValue = "stub")
    SignaturePort stubSignaturePort(
            SignatureProperties properties,
            Environment environment,
            TenantProperties tenantProperties,
            SignatureRequestRepository repository,
            DocumentStoragePort storage,
            MeterRegistry meterRegistry) {
        Set<String> profiles = Set.of(environment.getActiveProfiles());
        SignatureStubGuard.Decision decision =
                SignatureStubGuard.evaluate(profiles, properties.stub().allowOutsideLocal());
        if (decision == SignatureStubGuard.Decision.REFUSED) {
            throw new IllegalStateException(SignatureStubGuard.refusalMessage(profiles));
        }
        boolean outsideLocal = decision == SignatureStubGuard.Decision.ALLOWED_WITH_WARNING;
        if (outsideLocal) {
            LOG.warn("SIGNATURE STUB ACTIVE OUTSIDE local/test PROFILES (app.signature.stub.allow-outside-local="
                    + "true): signatures are SIMULATED and have NO legal value.");
        } else {
            LOG.info("Signature stub active (profile local/test): signatures are SIMULATED.");
        }
        Gauge.builder("myrmacta.signature.stub.active", () -> 1)
                .tag("outside_local", String.valueOf(outsideLocal))
                .description("1 when the simulated signature provider is active")
                .register(meterRegistry);
        return new StubSignatureAdapter(tenantProperties.id(), repository, storage);
    }

    /** TODO §109 (D-SIGNATURE-PROVIDER) — un seul adaptateur actif, ici Yousign. */
    @Bean
    @ConditionalOnProperty(prefix = "app.signature", name = "provider", havingValue = "yousign")
    SignaturePort yousignSignaturePort(TenantProperties tenantProperties, ConnectorInstanceRepository instances) {
        return new YousignSignatureAdapter(tenantProperties.id(), instances);
    }

    /** TODO §109 (D-SIGNATURE-PROVIDER) — un seul adaptateur actif, ici DocuSign. */
    @Bean
    @ConditionalOnProperty(prefix = "app.signature", name = "provider", havingValue = "docusign")
    SignaturePort docusignSignaturePort(
            TenantProperties tenantProperties, ConnectorInstanceRepository instances, PortalProperties portal) {
        return new DocusignSignatureAdapter(tenantProperties.id(), instances, portal.publicBaseUrl());
    }

    /**
     * TODO §138 (D-EID-VALIDATION) — vérificateur réel, fondé sur DSS. Les autorités sont celles de
     * l'exploitant (D-EID-TRUST) ; la révocation passe par un transport borné (D-EID-REVOCATION).
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.signature", name = "provider", havingValue = "eid")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
            be.myrmacta.infrastructure.signature.eid.SignedPdfVerifier.class)
    be.myrmacta.infrastructure.signature.eid.SignedPdfVerifier eidSignedPdfVerifier(
            EidTrustAnchors trustAnchors, PinnedRevocationTransport eidRevocationTransport) {
        return new be.myrmacta.infrastructure.signature.eid.DssSignedPdfVerifier(
                trustAnchors.certificates(), java.time.Clock.systemUTC(), eidRevocationTransport);
    }

    /** TODO §138 (D-EID-REVOCATION) — transport de révocation aux réglages {@code app.signature.eid.revocation.*}. */
    @Bean
    @ConditionalOnProperty(prefix = "app.signature", name = "provider", havingValue = "eid")
    PinnedRevocationTransport eidRevocationTransport(SignatureProperties properties) {
        SignatureProperties.Revocation revocation = properties.eid().revocation();
        return new PinnedRevocationTransport(revocation.timeout(), revocation.maxResponseBytes());
    }

    /**
     * TODO §138 (D-EID-TIMESTAMP) — horodatage du document final, seulement si l'exploitant a configuré
     * {@code app.signature.eid.timestamp.url} ; sinon {@code null} (aucune TSA par défaut).
     */
    static DssDocumentTimestamper eidTimestamper(
            SignatureProperties properties, EidTrustAnchors trustAnchors, PinnedRevocationTransport revocation) {
        SignatureProperties.Timestamp timestamp = properties.eid().timestamp();
        if (!timestamp.enabled()) {
            return null;
        }
        return new DssDocumentTimestamper(timestamp.url(), trustAnchors.certificates(), revocation,
                timestamp.timeout(), timestamp.maxResponseBytes());
    }

    /**
     * TODO §138 (D-EID-TRUST) — autorités de confiance du fournisseur {@code eid}, lues au démarrage : un
     * fichier absent, illisible ou sans certificat fait échouer le contexte avec un message explicite. Le
     * vérificateur ({@link SignedPdfVerifier}) les prend en paramètre.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.signature", name = "provider", havingValue = "eid")
    EidTrustAnchors eidTrustAnchors(SignatureProperties properties) {
        return EidTrustAnchors.load(properties.eid().trustAnchorsFile());
    }

    /**
     * TODO §138 (D-EID-UPLOAD) — signature par carte eID : le signataire téléverse le PDF qu'il a signé, que le
     * {@link SignedPdfVerifier} vérifie. Le vérificateur est un bean à part (bibliothèque de validation).
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.signature", name = "provider", havingValue = "eid")
    SignaturePort eidSignaturePort(
            TenantProperties tenantProperties,
            SignatureRequestRepository repository,
            SignedUploadRepository uploads,
            DocumentStoragePort storage,
            SignedPdfVerifier verifier,
            EidTrustAnchors trustAnchors,
            SignatureProperties properties,
            PinnedRevocationTransport eidRevocationTransport) {
        DssDocumentTimestamper timestamper = eidTimestamper(properties, trustAnchors, eidRevocationTransport);
        LOG.info("Signature provider eid active: {} trusted certificate authorities, document timestamp {}.",
                trustAnchors.certificates().size(), timestamper == null ? "off" : "on");
        return new EidSignatureAdapter(tenantProperties.id(), repository, uploads, storage, verifier, timestamper,
                properties.dispatch().maxAttempts());
    }

    @Bean
    SignatureAvailabilityPort signatureAvailabilityPort(SignatureProperties properties, PortalProperties portal) {
        return new SignatureAvailabilityPort() {
            @Override
            public boolean providerActive() {
                return properties.providerActive();
            }

            @Override
            public boolean portalEnabled() {
                return portal.enabled();
            }

            @Override
            public int maxSigners() {
                return properties.maxSigners();
            }

            @Override
            public int defaultDeadlineDays() {
                return properties.defaultDeadlineDays();
            }

            @Override
            public int maxDeadlineDays() {
                return properties.maxDeadlineDays();
            }

            @Override
            public long maxDocumentBytes() {
                return properties.maxDocumentBytes();
            }

            @Override
            public long maxSignedUploadBytes() {
                return properties.eid().maxUploadBytes();
            }
        };
    }
}
