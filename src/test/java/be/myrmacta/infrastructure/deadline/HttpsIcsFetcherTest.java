package be.myrmacta.infrastructure.deadline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * TODO §121 (D-CAL-ICS-URL) — téléchargement d'un calendrier par URL : garde réseau (§44), aucune redirection,
 * 1 Mio, délai total, aucun en-tête d'authentification, et aucune erreur qui reprenne l'URL (un jeton y est souvent).
 */
class HttpsIcsFetcherTest {

    private static final String SECRET = "TOPSECRET-TOKEN";
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    private HttpServer serve(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        servers.add(server);
        return server;
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/ical/" + SECRET + "/basic.ics?key=" + SECRET;
    }

    private static HttpsIcsFetcher fetcher() {
        return new HttpsIcsFetcher(new OutboundUrlGuard(true), Duration.ofSeconds(2), Duration.ofMillis(600), Duration.ofMillis(1500));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    @Test
    void aSuccessfulDownloadReturnsTheBytes_withoutAnyCredentialHeader() throws Exception {
        List<String> seen = new ArrayList<>();
        HttpServer server = serve(exchange -> {
            seen.add(exchange.getRequestMethod());
            exchange.getRequestHeaders().keySet().forEach(name -> seen.add(name.toLowerCase()));
            respond(exchange, 200, "BEGIN:VCALENDAR".getBytes(StandardCharsets.UTF_8));
        });

        byte[] body = fetcher().fetch(url(server));

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("BEGIN:VCALENDAR");
        assertThat(seen).contains("GET").doesNotContain("authorization", "cookie", "proxy-authorization");
    }

    @Test
    void aBodyOfExactlyOneMebibyteIsAccepted() throws Exception {
        byte[] exact = new byte[1024 * 1024];
        HttpServer server = serve(exchange -> respond(exchange, 200, exact));

        assertThat(fetcher().fetch(url(server))).hasSize(1024 * 1024);
    }

    @Test
    void aResponseThatDeclaresMoreThanOneMebibyteIsRefused() throws Exception {
        HttpServer server = serve(exchange -> respond(exchange, 200, new byte[1024 * 1024 + 1]));

        assertThatThrownBy(() -> fetcher().fetch(url(server)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 MiB")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void anEndlessChunkedResponseIsCutOffAtOneMebibyte_notAtTheDeadline() throws Exception {
        HttpServer server = serve(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                byte[] chunk = new byte[64 * 1024];
                for (int i = 0; i < 4_000; i++) {
                    out.write(chunk);
                }
            } catch (IOException clientHungUp) {
                // le client a coupé : c'est ce qu'on attend
            }
            exchange.close();
        });

        // Le message dit « 1 MiB » et non « timed out » : c'est la taille, pas le délai, qui a coupé l'appel.
        assertThatThrownBy(() -> fetcher().fetch(url(server)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 MiB")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void aRedirectIsNeverFollowed() throws Exception {
        AtomicInteger targetHits = new AtomicInteger();
        HttpServer target = serve(exchange -> {
            targetHits.incrementAndGet();
            respond(exchange, 200, "BEGIN:VCALENDAR".getBytes(StandardCharsets.UTF_8));
        });
        HttpServer origin = serve(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + target.getAddress().getPort() + "/internal");
            respond(exchange, 302, new byte[0]);
        });

        assertThatThrownBy(() -> fetcher().fetch(url(origin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("302")
                .hasMessageContaining("redirect")
                .hasMessageNotContaining(SECRET);
        assertThat(targetHits).hasValue(0);
    }

    @Test
    void anErrorStatusIsReportedWithItsCodeAndTheHostOnly() throws Exception {
        HttpServer server = serve(exchange -> respond(exchange, 404, "pas ici".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> fetcher().fetch(url(server)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("127.0.0.1")
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining("basic.ics");
    }

    @Test
    void aServerThatDripsBytesIsStoppedByTheTotalDeadline() throws Exception {
        HttpServer server = serve(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    out.write('x');
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException clientHungUp) {
                // le client a coupé
            }
            exchange.close();
        });
        long start = System.nanoTime();

        assertThatThrownBy(() -> fetcher().fetch(url(server)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timed out")
                .hasMessageNotContaining(SECRET);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    // TODO §121 (revue de #33) — le délai de 10 s couvre aussi la connexion et les en-têtes, pas seulement le corps : chaque
    // ligne d'en-tête arrive avant le délai de lecture, donc sans délai total l'appel resterait ouvert (100 × 300 ms).
    @Test
    @Timeout(10)
    void aServerThatDripsResponseHeadersIsStoppedByTheTotalDeadline() throws Exception {
        ServerSocket raw = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Thread server = new Thread(() -> {
            try (Socket client = raw.accept()) {
                byte[] buffer = new byte[4096];
                client.getInputStream().read(buffer);
                OutputStream out = client.getOutputStream();
                out.write("HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.US_ASCII));
                for (int i = 0; i < 100; i++) {
                    out.write(("X-Drip-" + i + ": v\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    Thread.sleep(300);
                }
            } catch (IOException | InterruptedException clientHungUp) {
                // le client a coupé : c'est ce qu'on attend
            }
        });
        server.setDaemon(true);
        server.start();
        long start = System.nanoTime();
        try {
            assertThatThrownBy(() -> fetcher().fetch("http://127.0.0.1:" + raw.getLocalPort() + "/ical/" + SECRET + "/basic.ics"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timed out")
                    .hasMessageNotContaining(SECRET);

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        } finally {
            raw.close();
        }
    }

    @Test
    void aServerThatNeverAnswersTimesOut() throws Exception {
        HttpServer server = serve(exchange -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        long start = System.nanoTime();

        assertThatThrownBy(() -> fetcher().fetch(url(server)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timed out")
                .hasMessageNotContaining(SECRET);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void anUnreachableHostIsReportedWithoutTheUrl() throws Exception {
        HttpServer closed = serve(exchange -> respond(exchange, 200, new byte[0]));
        String url = url(closed);
        closed.stop(0);

        assertThatThrownBy(() -> fetcher().fetch(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be reached")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void withoutTheLocalHttpAllowanceAnHttpLoopbackUrlIsRefusedAndNeverCalled() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = serve(exchange -> {
            hits.incrementAndGet();
            respond(exchange, 200, new byte[0]);
        });
        HttpsIcsFetcher production = new HttpsIcsFetcher(new OutboundUrlGuard(false), Duration.ofSeconds(2), Duration.ofMillis(600),
                Duration.ofMillis(1500));

        assertThatThrownBy(() -> production.fetch(url(server)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refused")
                .hasMessageNotContaining(SECRET);
        assertThat(hits).hasValue(0);
    }

    @Test
    void internalAndMetadataAddressesAreRefusedEvenOverHttps() {
        HttpsIcsFetcher production = new HttpsIcsFetcher(new OutboundUrlGuard(false), Duration.ofSeconds(2), Duration.ofMillis(600),
                Duration.ofMillis(1500));
        for (String target : new String[] {"https://10.0.0.5/" + SECRET, "https://192.168.1.1/" + SECRET,
                "https://169.254.169.254/latest/meta-data/" + SECRET, "https://127.0.0.1/" + SECRET, "https://localhost/" + SECRET,
                "https://[::1]/" + SECRET, "https://0.0.0.0/" + SECRET, "https://2130706433/" + SECRET,
                // Revue de #33 : des graphies IPv6 qui joignent la boucle locale, le réseau interne ou les métadonnées.
                "https://[::ffff:7f00:1]/" + SECRET, "https://[::ffff:a9fe:a9fe]/" + SECRET, "https://[0:0:0:0:0:0:0:1]/" + SECRET,
                "https://[::]/" + SECRET, "https://[64:ff9b::7f00:1]/" + SECRET, "https://[::7f00:1]/" + SECRET,
                "https://[2002:7f00:1::]/" + SECRET}) {
            assertThatThrownBy(() -> production.fetch(target)).as(target)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refused")
                    .hasMessageNotContaining(SECRET);
        }
    }

    @Test
    void otherSchemesAreRefused() {
        for (String target : new String[] {"file:///etc/passwd", "ftp://example.org/" + SECRET, "gopher://example.org/" + SECRET}) {
            assertThatThrownBy(() -> fetcher().fetch(target)).as(target)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(SECRET);
        }
    }
}
