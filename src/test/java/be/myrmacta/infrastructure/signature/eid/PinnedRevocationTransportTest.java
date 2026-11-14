package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Transport de révocation de production : garde d'adresse, redirection, taille, délai (TODO §138, D-EID-REVOCATION). */
class PinnedRevocationTransportTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ocsp", exchange -> {
            hits.incrementAndGet();
            byte[] request = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/ocsp-response");
            exchange.sendResponseHeaders(200, request.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(request);
            }
        });
        server.createContext("/redirect", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/big", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[4096]);
            } catch (IOException ignored) {
                // client a coupé
            }
        });
        server.createContext("/slow", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 50; i++) {
                    out.write('x');
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException ignored) {
                // client a coupé
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI local(String path) {
        return URI.create("http://revocation.test:" + server.getAddress().getPort() + path);
    }

    /** Transport de test : la garde est remplacée par une adresse figée sur la boucle locale (le serveur de test). */
    private PinnedRevocationTransport loopbackTransport(Duration total, int maxBytes) {
        return new PinnedRevocationTransport(uri -> InetAddress.getLoopbackAddress(), Duration.ofSeconds(2), total, maxBytes, maxBytes);
    }

    @Test
    @DisplayName("POST OCSP vers l'adresse figée : corps rendu")
    void postsOcsp() throws IOException {
        byte[] answer = loopbackTransport(Duration.ofSeconds(5), 1024).postOcsp(local("/ocsp"), new byte[] {1, 2, 3});

        assertThat(answer).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Garde de production : boucle locale, plage privée et métadonnées refusées sans connexion")
    void productionGuardRefusesInternalAddresses() {
        PinnedRevocationTransport transport = new PinnedRevocationTransport();

        assertThatThrownBy(() -> transport.getCrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ocsp")))
                .isInstanceOf(IOException.class).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> transport.postOcsp(URI.create("http://localhost:" + server.getAddress().getPort() + "/ocsp"), new byte[1]))
                .isInstanceOf(IOException.class).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> transport.getCrl(URI.create("http://169.254.169.254/latest/meta-data")))
                .isInstanceOf(IOException.class).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> transport.getCrl(URI.create("http://10.1.2.3/ca.crl")))
                .isInstanceOf(IOException.class).hasMessageContaining("not allowed");
        assertThat(hits).hasValue(0);
    }

    @Test
    @DisplayName("Garde de production : un nom public qui se résout en boucle locale est refusé")
    void dnsNameResolvingToLoopbackIsRefused() {
        PinnedRevocationTransport transport = new PinnedRevocationTransport(
                PinnedRevocationTransport.guarded(new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getLoopbackAddress()})),
                Duration.ofSeconds(2), Duration.ofSeconds(5), 1024, 1024);

        assertThatThrownBy(() -> transport.getCrl(local("/ocsp"))).isInstanceOf(IOException.class).hasMessageContaining("not allowed");
        assertThat(hits).hasValue(0);
    }

    @Test
    @DisplayName("Schéma autre que http(s) refusé")
    void otherSchemesRefused() {
        PinnedRevocationTransport transport = loopbackTransport(Duration.ofSeconds(5), 1024);

        assertThatThrownBy(() -> transport.getCrl(URI.create("ldap://directory.test/cn=CA"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> transport.getCrl(URI.create("file:///etc/passwd"))).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Redirection non suivie")
    void redirectNotFollowed() {
        assertThatThrownBy(() -> loopbackTransport(Duration.ofSeconds(5), 1024).getCrl(local("/redirect")))
                .isInstanceOf(IOException.class).hasMessageContaining("redirect");
        assertThat(hits).hasValue(1);
    }

    @Test
    @DisplayName("Corps trop gros : refusé")
    void bodyTooLarge() {
        assertThatThrownBy(() -> loopbackTransport(Duration.ofSeconds(5), 1024).getCrl(local("/big")))
                .isInstanceOf(IOException.class).hasMessageContaining("too large");
    }

    @Test
    @DisplayName("Corps distillé au-delà du délai total : coupé (le délai couvre la lecture du corps)")
    void slowBodyTimesOut() {
        long started = System.nanoTime();

        assertThatThrownBy(() -> loopbackTransport(Duration.ofMillis(800), 1024 * 1024).getCrl(local("/slow")))
                .isInstanceOf(IOException.class).hasMessageContaining("timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
    }
}
