package be.myrmacta.infrastructure.signature;

import be.myrmacta.infrastructure.signature.eid.SignedPdfVerifier;
import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * TODO §138 — contexte Spring avec le fournisseur {@code eid} : autorités de test, faux vérificateur
 * ({@link FakeSignedPdfVerifier}, le vrai bean est câblé ailleurs), portail activé, plafonds bas pour tester la
 * taille. Même schéma que {@link AbstractSignatureEngineTest} (qui reste sur le stub). Le cœur BPMN reste celui de
 * {@code signature-smoke} (externes) et {@code signature-internal-smoke} (internes, séquentiel).
 *
 * <p>Précédent : l'exécuteur asynchrone de Flowable reste coupé par le profil de test ; le répartiteur est appelé
 * à la main ({@code DispatchSignatureOutcomesUseCase}), comme pour les fournisseurs hébergés.
 */
@SpringBootTest(properties = {
        "app.signature.provider=eid",
        "app.signature.eid.trust-anchors-file=src/test/resources/eid/test-anchors.pem",
        "app.signature.max-document-bytes=100000",
        "app.signature.eid.max-upload-bytes=120000",
        "app.signature.send.poll-delay=1h",
        "app.signature.expiry.poll-delay=1h",
        "app.signature.dispatch.poll-delay=1h",
        "app.signature.send.resend-min-interval=0s",
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h",
        "flowable.async-executor-activate=false"
})
@ActiveProfiles("test")
@Import({OidcClientTestConfiguration.class, AbstractEidSignatureEngineTest.FakeVerifierConfig.class})
abstract class AbstractEidSignatureEngineTest extends AbstractSignatureEngineTest {

    @TestConfiguration
    static class FakeVerifierConfig {
        @Bean
        SignedPdfVerifier fakeSignedPdfVerifier() {
            return new FakeSignedPdfVerifier();
        }
    }

    @BeforeEach
    void resetFakeVerifier() {
        FakeSignedPdfVerifier.reset();
    }
}
