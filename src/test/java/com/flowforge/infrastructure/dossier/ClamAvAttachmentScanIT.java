package com.flowforge.infrastructure.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import com.flowforge.infrastructure.config.AttachmentScanProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
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
 * <p>Isolée du run standard {@code ./mvnw verify} par simple nommage {@code *IT.java}
 * (D-AV-CLAMAV-TEST-SCOPE — le dépôt n'a pas de {@code maven-failsafe-plugin}, et les patterns
 * par défaut de {@code maven-surefire-plugin} n'incluent pas {@code *IT.java}, vérifié en Phase
 * 4 planning). Exécution explicite : {@code ./mvnw test -Dtest=ClamAvAttachmentScanIT}.
 *
 * <p><strong>⚠️ Non exécuté dans ce bac à sable</strong> (Docker absent, même limite que {@code
 * TenantSchemaIsolationIT}/{@code D-TESTCONTAINERS-EXEC}) — écrit et compilé (vérifié via {@code
 * ./mvnw test-compile}) ; exécution/vérification réelle à faire dans un environnement avec
 * Docker (CI, ou poste de développement avec le démon Docker actif).
 */
class ClamAvAttachmentScanIT {

    // Signature de test standard, publique, normalisée par l'industrie AV (eicar.org) —
    // détectée par tout moteur antivirus conforme sans être un vrai virus.
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Test
    void realClamd_detectsEicarAndAcceptsCleanFile() {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker indisponible dans ce bac à sable (D-TESTCONTAINERS-EXEC) — exécuté en CI"
                        + " (ubuntu-latest, Docker natif) ou sur un poste avec Docker actif");

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
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker indisponible dans ce bac à sable (D-TESTCONTAINERS-EXEC) — exécuté en CI"
                        + " (ubuntu-latest, Docker natif) ou sur un poste avec Docker actif");

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
