package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §115 (D-HTTP-PATCH) — le client joint l'adresse figée, pas un second DNS, et n'envoie pas PATCH à une redirection. */
class PinnedHttpClientTest {

    private HttpServer server;
    private HttpServer elsewhere;
    private volatile Thread stalled;

    @AfterEach
    void stop() {
        if (stalled != null) {
            stalled.interrupt();
        }
        if (server != null) {
            server.stop(0);
        }
        if (elsewhere != null) {
            elsewhere.stop(0);
        }
    }

    @Test
    void patchReachesThePinnedAddressWithoutResolvingTheName() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/vendors", exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        HttpRestConnector.HttpCallResponse response = PinnedHttpClient.sender().send(request(
                "http://pinned.invalid:" + port + "/vendors",
                "PATCH",
                "{\"Name\":\"A\"}".getBytes(StandardCharsets.UTF_8),
                1_000,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"ok\":true}");
        assertThat(method).hasValue("PATCH");
        assertThat(body).hasValue("{\"Name\":\"A\"}");
        assertThat(host.get()).isEqualTo("pinned.invalid:" + port);
    }

    @Test
    void connectorSendsPatchThroughThePinnedClient() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/x", exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        HttpRestConnector connector = new HttpRestConnector(
                new SimpleJsonPathMapper(),
                new OutboundUrlGuard(true),
                JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null),
                PinnedHttpClient.sender());
        ConnectorInstanceData instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "api", "http-rest", true, Map.of());

        Map<String, Object> result = connector.execute(instance, Map.of(
                "url", "http://localhost:" + server.getAddress().getPort() + "/x",
                "method", "PATCH",
                "body", Map.of("k", "v")));

        assertThat(result.get("status")).isEqualTo(200);
        assertThat(method).hasValue("PATCH");
        assertThat(body.get()).contains("\"k\"");
    }

    @Test
    void redirectIsNotFollowed() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        elsewhere = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        elsewhere.createContext("/secret", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        elsewhere.start();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/move", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        HttpRestConnector.HttpCallResponse response = PinnedHttpClient.sender().send(request(
                "http://pinned.invalid:" + server.getAddress().getPort() + "/move",
                "GET",
                null,
                1_000,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)));

        assertThat(response.status()).isEqualTo(302);
        assertThat(hits).hasValue(0);
    }

    @Test
    void responseLargerThanTheBoundIsRejected() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/big", exchange -> {
            byte[] out = "0123456789".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> PinnedHttpClient.sender().send(request(
                        "http://pinned.invalid:" + server.getAddress().getPort() + "/big",
                        "GET",
                        null,
                        4,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxResponseBytes");
    }

    @Test
    void readTimeoutBoundsTheBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stall", exchange -> {
            stalled = Thread.currentThread();
            exchange.sendResponseHeaders(200, 20);
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(() -> PinnedHttpClient.sender().send(request(
                        "http://pinned.invalid:" + server.getAddress().getPort() + "/stall",
                        "GET",
                        null,
                        1_000,
                        Duration.ofMillis(200),
                        Duration.ofMillis(300))))
                .isInstanceOf(IOException.class));
    }

    @Test
    void tlsChecksTheOriginalNameWhileConnectingToThePinnedAddress() throws Exception {
        KeyStore store = keyStore();
        SSLContext serverContext = serverContext(store);
        SSLContext trust = trustContext(store);
        HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        https.createContext("/odata", exchange -> {
            byte[] out = "patched".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        https.start();
        server = https;
        int port = https.getAddress().getPort();

        HttpRestConnector.HttpCallResponse ok = PinnedHttpClient.sender(trust).send(request(
                "https://pinned.invalid:" + port + "/odata",
                "PATCH",
                "{}".getBytes(StandardCharsets.UTF_8),
                1_000,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body()).isEqualTo("patched");

        assertThatThrownBy(() -> PinnedHttpClient.sender(trust).send(request(
                        "https://other.invalid:" + port + "/odata",
                        "PATCH",
                        "{}".getBytes(StandardCharsets.UTF_8),
                        1_000,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2))))
                .isInstanceOf(IOException.class);
    }

    private static SSLContext serverContext(KeyStore store) throws Exception {
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "changeit".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }

    private static SSLContext trustContext(KeyStore store) throws Exception {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return context;
    }

    private static KeyStore keyStore() throws Exception {
        Path dir = Files.createTempDirectory("http-rest-patch");
        Path store = dir.resolve("store.p12");
        Process keytool = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                        "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                        "-storetype", "PKCS12", "-keystore", store.toString(),
                        "-storepass", "changeit", "-keypass", "changeit",
                        "-dname", "CN=pinned.invalid", "-ext", "SAN=dns:pinned.invalid",
                        "-validity", "1", "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream in = keytool.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (keytool.waitFor() != 0) {
            throw new IllegalStateException(output);
        }
        KeyStore loaded = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            loaded.load(in, "changeit".toCharArray());
        }
        return loaded;
    }

    private static HttpRestConnector.HttpCallRequest request(
            String url, String method, byte[] body, long max, Duration connect, Duration read) throws Exception {
        return new HttpRestConnector.HttpCallRequest(
                url,
                InetAddress.getByName("127.0.0.1"),
                method,
                body,
                java.util.Map.of("Host", "pinned.invalid"),
                connect,
                read,
                "pinned.invalid",
                max);
    }
}
