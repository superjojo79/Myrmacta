package be.myrmacta.infrastructure.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.infrastructure.config.AttachmentScanProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Preuve d'exécution réelle (D-AV-CLAMAV-TEST-SCOPE) contre un vrai moteur {@code clamd}
 * (Testcontainers), pas seulement contre le faux serveur socket de {@link
 * ClamAvAttachmentScanAdapterTest} — même esprit que {@code DmnJuelSecuritySmokeIT} : ne prouve
 * pas que le chemin HTTP complet fonctionne (déjà couvert, mocké, par {@code
 * DossierElementUploadControllerTest}), prouve que le protocole {@code INSTREAM} implémenté à la
 * main dans {@link ClamAvAttachmentScanAdapter} est effectivement compris par un vrai moteur
 * ClamAV, pas seulement par notre propre simulation de {@code clamd}.
 *
 * <p>Hors du run standard {@code ./mvnw verify} (D-AV-CLAMAV-TEST-SCOPE, TODO §125) : surefire inclut
 * les {@code *IT} depuis #157, d'où la garde explicite {@code -Dclamav.it=true}, sans laquelle le
 * démarrage de {@code clamd} (signatures) alourdirait chaque build d'un poste où Docker ou Podman
 * tourne. Avec la propriété, l'absence de démon est un échec, pas un test ignoré. Exécution : {@code
 * ./mvnw test -Dtest=ClamAvAttachmentScanIT -Dclamav.it=true}, ou le workflow à la demande
 * {@code recette-env-reel.yml}.
 */
class ClamAvAttachmentScanIT {

    // Signature de test standard, publique, normalisée par l'industrie AV (eicar.org) —
    // détectée par tout moteur antivirus conforme sans être un vrai virus.
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @BeforeEach
    void requiresAnExplicitRealClamdRun() {
        Assumptions.assumeTrue(Boolean.getBoolean("clamav.it"), "-Dclamav.it=true requis (TODO §125)");
        assertThat(DockerClientFactory.instance().isDockerAvailable())
                .as("-Dclamav.it=true sans démon Docker ou Podman joignable")
                .isTrue();
    }

    @Test
    void realClamd_detectsEicarAndAcceptsCleanFile() {
        try (GenericContainer<?> clamd = clamdContainer()) {
            clamd.start();
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, new SimpleMeterRegistry());

            AttachmentScanResult infected = adapter.scan(EICAR.getBytes(StandardCharsets.US_ASCII), "text/plain");
            assertThat(infected.clean()).isFalse();
            assertThat(infected.reason()).contains("Eicar");

            AttachmentScanResult clean = adapter.scan("hello world".getBytes(StandardCharsets.US_ASCII), "text/plain");
            assertThat(clean.clean()).isTrue();
        }
    }

    @Test
    void clamdStoppedMidway_isFailOpenWithCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try (GenericContainer<?> clamd = clamdContainer()) {
            clamd.start();
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, meterRegistry);

            AttachmentScanResult whileUp = adapter.scan("hello world".getBytes(StandardCharsets.US_ASCII), "text/plain");
            assertThat(whileUp.clean()).isTrue();

            clamd.stop();

            AttachmentScanResult afterStop = adapter.scan("hello world".getBytes(StandardCharsets.US_ASCII), "text/plain");
            assertThat(afterStop.clean()).isTrue();
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        }
    }

    private static GenericContainer<?> clamdContainer() {
        // Même tag épinglé que le profil docker-compose (D-AV-CLAMAV-IMAGE) ; démarrage lent
        // (chargement des signatures freshclam), budget aligné sur le start_period Compose.
        return new GenericContainer<>(DockerImageName.parse("clamav/clamav:1.4"))
                .withExposedPorts(3310)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(5));
    }

    private static ClamAvAttachmentScanAdapter adapterFor(GenericContainer<?> clamd, SimpleMeterRegistry meterRegistry) {
        return new ClamAvAttachmentScanAdapter(
                new AttachmentScanProperties.ClamAv(
                        clamd.getHost(),
                        clamd.getMappedPort(3310),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30)),
                meterRegistry);
    }
}
