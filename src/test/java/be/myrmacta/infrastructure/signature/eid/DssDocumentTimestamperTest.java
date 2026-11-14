package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.TestTsa;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Horodatage RFC 3161 du document signé final (TODO §138, D-EID-TIMESTAMP). */
class DssDocumentTimestamperTest {

    private static final URI TSA_URL = URI.create("http://tsa.example.test/tsp");

    private static EidTestPki pki;
    private static byte[] base;
    private static byte[] signed;

    @BeforeAll
    static void setUp() {
        pki = EidTestPki.create("Timestamp Test");
        base = EidTestPdfs.basePdf();
        signed = EidTestPdfs.sign(base, pki.qualifiedSigner(KeyType.RSA_2048));
    }

    private static DssDocumentTimestamper timestamper(TestTsa tsa) {
        return new DssDocumentTimestamper(TSA_URL, pki.trustedFile(), pki.transport(), (url, query) -> tsa.respond(query));
    }

    private static SignedUploadVerdict verify(byte[] previous, byte[] extended) {
        return new DssSignedPdfVerifier(pki.trustedFile(), EidTestPki.CLOCK, pki.transport()).verify(previous, extended, Set.of());
    }

    @Test
    @DisplayName("TSA rattachée aux autorités : PAdES-BASELINE-LTA, prolongement accepté par le vérificateur")
    void trustedTsaGivesLongTermArchive() {
        TestTsa tsa = pki.trustedTsa();

        DocumentTimestamper.Result result = timestamper(tsa).timestamp(signed);

        assertThat(result.level()).isEqualTo(DssDocumentTimestamper.LEVEL_LTA);
        assertThat(result.authorityTrusted()).isTrue();
        assertThat(result.authority()).contains("Timestamp Test TSA");
        assertThat(result.tokenTime()).isEqualTo(EidTestPki.NOW.minusSeconds(10));
        assertThat(Arrays.mismatch(signed, Arrays.copyOf(result.document(), signed.length))).isEqualTo(-1);
        assertThat(tsa.callCount()).isGreaterThanOrEqualTo(2);
        // Le document étendu prolonge le dernier téléversement : le vérificateur l'admet (révisions d'extension).
        SignedUploadVerdict verdict = verify(base, result.document());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("TSA hors des autorités : horodatage de document seul, dit non rattaché, toujours accepté")
    void untrustedTsaGivesDocumentTimestampOnly() {
        DocumentTimestamper.Result result = timestamper(TestTsa.untrusted()).timestamp(signed);

        assertThat(result.level()).isEqualTo(DssDocumentTimestamper.LEVEL_DOCUMENT_TIMESTAMP);
        assertThat(result.authorityTrusted()).isFalse();
        assertThat(result.authority()).contains("Test TSA");
        SignedUploadVerdict verdict = verify(base, result.document());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("TSA injoignable : TimestampUnavailableException, aucun document rendu")
    void unavailableTsa() {
        assertThatThrownBy(() -> timestamper(pki.trustedTsa().unavailable()).timestamp(signed))
                .isInstanceOf(RfcTimestampSource.TimestampUnavailableException.class);
    }

    @Test
    @DisplayName("Réponse falsifiée (empreinte du message différente) : refusée")
    void forgedResponseRefused() {
        assertThatThrownBy(() -> timestamper(pki.trustedTsa().forged()).timestamp(signed))
                .isInstanceOf(RfcTimestampSource.TimestampUnavailableException.class);
    }

    @Test
    @DisplayName("Réponse qui n'est pas un horodatage : refusée")
    void garbageResponseRefused() {
        DssDocumentTimestamper timestamper = new DssDocumentTimestamper(TSA_URL, pki.trustedFile(), pki.transport(),
                (url, query) -> "pas une réponse".getBytes());

        assertThatThrownBy(() -> timestamper.timestamp(signed)).isInstanceOf(RfcTimestampSource.TimestampUnavailableException.class);
    }

    @Test
    @DisplayName("Transport de la TSA : TSA interne admise, délai total et taille de réponse bornés, pas de redirection")
    void transportIsBounded() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        TestTsa tsa = pki.trustedTsa();
        server.createContext("/tsp", exchange -> {
            byte[] answer = tsa.respond(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.createContext("/big", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[8192]);
            } catch (IOException ignored) {
                // coupé
            }
        });
        server.createContext("/slow", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 40; i++) {
                    out.write('x');
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException ignored) {
                // coupé
            }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            String root = "http://127.0.0.1:" + server.getAddress().getPort();
            // TSA interne (boucle locale) : légitime, l'adresse vient de l'exploitant.
            DocumentTimestamper.Result result = new DssDocumentTimestamper(root + "/tsp", pki.trustedFile(), pki.transport(),
                    Duration.ofSeconds(5), 64 * 1024).timestamp(signed);
            assertThat(result.document().length).isGreaterThan(signed.length);

            assertThatThrownBy(() -> new DssDocumentTimestamper(root + "/big", pki.trustedFile(), pki.transport(),
                    Duration.ofSeconds(5), 1024).timestamp(signed))
                    .isInstanceOf(RfcTimestampSource.TimestampUnavailableException.class).hasMessageContaining("too large");
            long started = System.nanoTime();
            assertThatThrownBy(() -> new DssDocumentTimestamper(root + "/slow", pki.trustedFile(), pki.transport(),
                    Duration.ofMillis(700), 64 * 1024).timestamp(signed))
                    .isInstanceOf(RfcTimestampSource.TimestampUnavailableException.class).hasMessageContaining("timed out");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
            assertThatThrownBy(() -> new DssDocumentTimestamper(root + "/redirect", pki.trustedFile(), pki.transport(),
                    Duration.ofSeconds(5), 64 * 1024).timestamp(signed))
                    .isInstanceOf(RfcTimestampSource.TimestampUnavailableException.class).hasMessageContaining("redirect");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Adresse de TSA : http ou https seulement, sans identifiants")
    void urlIsChecked() {
        assertThatThrownBy(() -> DssDocumentTimestamper.checkedUrl("ftp://tsa.test/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DssDocumentTimestamper.checkedUrl("https://user:pw@tsa.test/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DssDocumentTimestamper.checkedUrl(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(DssDocumentTimestamper.checkedUrl("https://tsa.test/tsp")).hasHost("tsa.test");
    }

    @Test
    @DisplayName("Transport de révocation aux réglages de l'exploitant (app.signature.eid.revocation.*)")
    void revocationTransportHonoursSettings() {
        PinnedRevocationTransport transport = new PinnedRevocationTransport(Duration.ofSeconds(3), 2048);

        assertThat(transport.totalTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(transport.maxOcspBytes()).isEqualTo(2048);
        assertThatThrownBy(() -> new PinnedRevocationTransport(Duration.ZERO, 2048)).isInstanceOf(IllegalArgumentException.class);
    }
}
