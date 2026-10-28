package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Client HTTP Yousign contre un serveur fictif (TODO §109, D-SIGNATURE-HTTP). */
class YousignHttpClientTest {

    private static final String API_KEY = "ys-secret-key-not-for-logs";
    private static final UUID REQUEST_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");

    private HttpServer server;
    private final List<String> calls = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private final AtomicInteger elsewhereHits = new AtomicInteger();
    private HttpServer elsewhere;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            byte[] raw = exchange.getRequestBody().readAllBytes();
            String body = new String(raw, StandardCharsets.UTF_8);
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            calls.add(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query) + " " + body);
            byte[] out = route(path, exchange.getRequestMethod()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(201, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (elsewhere != null) {
            elsewhere.stop(0);
        }
    }

    @Test
    void createSendsDeliveryModeNoneAndTheBearerKey() {
        YousignHttpClient client = client();

        UUID id = client.createSignatureRequest("Contrat", true, "req-1", "2026-11-11");

        assertThat(id).isEqualTo(REQUEST_ID);
        assertThat(calls).singleElement().asString()
                .contains("POST /v3/signature_requests")
                .contains("\"delivery_mode\":\"none\"")
                .contains("\"ordered_signers\":true")
                .contains("\"external_id\":\"req-1\"")
                .contains("\"expiration_date\":\"2026-11-11\"");
        assertThat(authorizations).containsExactly("Bearer " + API_KEY);
    }

    @Test
    void uploadAsksForAnchorParsing() {
        client().uploadSignablePdf(REQUEST_ID, "contrat.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assertThat(calls).singleElement().asString()
                .contains("POST /v3/signature_requests/" + REQUEST_ID + "/documents")
                .contains("name=\"parse_anchors\"")
                .contains("name=\"nature\"")
                .contains("signable_document")
                .contains("%PDF-1.4");
    }

    @Test
    void redirectIsNotFollowedAndTheKeyStaysOutOfTheError() throws IOException {
        elsewhere = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        elsewhere.createContext("/", exchange -> {
            elsewhereHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        elsewhere.start();
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/stolen");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> client().createSignatureRequest("Contrat", false, "req-1", null))
                .isInstanceOf(YousignCallException.class)
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("stolen");
        assertThat(elsewhereHits).hasValue(0);
    }

    @Test
    void signingLinkOnAForeignHostIsRejectedWithoutEchoingTheLink() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String json = path.endsWith("/signers")
                    ? "[{\"id\":\"11111111-1111-1111-1111-111111111111\"}]"
                    : "{\"signature_link\":\"https://evil.example/sign?token=" + API_KEY + "\"}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });

        assertThatThrownBy(() -> client().signatureLink(REQUEST_ID, 0))
                .isInstanceOf(YousignCallException.class)
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("evil.example");
    }

    @Test
    void signingLinkOnAYousignHostIsReturned() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String json = path.endsWith("/signers")
                    ? "[{\"id\":\"11111111-1111-1111-1111-111111111111\"}]"
                    : "{\"signature_link\":\"https://yousign.app/signatures/abc\"}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });

        assertThat(client().signatureLink(REQUEST_ID, 0)).contains("https://yousign.app/signatures/abc");
    }

    private YousignHttpClient client() {
        return new YousignHttpClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3"), API_KEY);
    }

    private static String route(String path, String method) {
        if (path.endsWith("/signature_requests") && "POST".equals(method)) {
            return "{\"id\":\"" + REQUEST_ID + "\"}";
        }
        if (path.endsWith("/documents") && "POST".equals(method)) {
            return "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"total_anchors\":1}";
        }
        return "{}";
    }
}
