package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.infrastructure.connector.builtin.EboxClient.Access;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.EboxResourceException;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.EboxTransportException;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.EboxUnauthorizedException;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.MessagePage;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TODO §150 (D-EBOX-REGISTRY-HOSTS) — client HTTP de l'eBox contre un serveur local : en-têtes, requêtes, corps binaire, bornes, statuts. */
class HttpEboxClientTest {

    private final Map<String, Response> routes = new ConcurrentHashMap<>();
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    private volatile String lastQuery;
    private HttpServer server;
    private String base;
    private HttpEboxClient client;

    private record Response(int status, String contentType, byte[] body) {}

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/REST/ebox";
        client = client(true);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpEboxClient client(boolean allowLoopback) {
        return new HttpEboxClient(new OutboundUrlGuard(allowLoopback), JsonMapper.builder().build(), Duration.ofSeconds(2),
                Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastQuery = exchange.getRequestURI().getRawQuery();
        exchange.getRequestHeaders().forEach((name, values) -> lastHeaders.put(name.toLowerCase(), values.getFirst()));
        Response response = routes.getOrDefault(exchange.getRequestURI().getRawPath(), new Response(404, "application/problem+json",
                "{\"title\":\"not found\"}".getBytes(StandardCharsets.UTF_8)));
        exchange.getResponseHeaders().add("Content-Type", response.contentType());
        exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
        exchange.getResponseBody().write(response.body());
        exchange.close();
    }

    private void json(String path, String body) {
        routes.put("/REST/ebox" + path, new Response(200, "application/json", body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void registriesAreListedWithTheBearerTokenAndTheMandateHeader() {
        json("/messageRegistries", "{\"items\":[{\"id\":\"socsec_v3\",\"endpoints\":[]}],\"totalItems\":1}");

        List<JsonNode> registries = client.registries(base + "/", new Access("secret-token", "0212345678"));

        assertThat(registries).extracting(node -> node.path("id").asString()).containsExactly("socsec_v3");
        assertThat(lastHeaders).containsEntry("authorization", "Bearer secret-token")
                .containsEntry("accept", "application/json")
                .containsEntry("on-behalf-of-enterprise-number", "0212345678");
    }

    @Test
    void theMandateHeaderIsAbsentWithoutMandate() {
        json("/messageRegistries", "{\"items\":[]}");

        client.registries(base, new Access("t", null));

        assertThat(lastHeaders).doesNotContainKey("on-behalf-of-enterprise-number");
    }

    @Test
    void messagesAreFilteredOnThePartitionAndTheReceptionDate() {
        json("/ebox/messages", "{\"items\":[{\"messageId\":\"m-1\"},{\"messageId\":\"m-2\"}],\"totalItems\":7}");

        MessagePage page = client.messages(base, new Access("t", null), "rh & paie", LocalDate.of(2026, 11, 6), 2, 100);

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalItems()).isEqualTo(7);
        assertThat(lastQuery).isEqualTo("recipientPartition=rh+%26+paie&receivedAfter=2026-11-06&page=2&pageSize=100");
    }

    @Test
    void anAttachmentIsReturnedByteForByte() {
        byte[] content = new byte[256];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        routes.put("/REST/ebox/ebox/messages/m%201/attachments/a%2F1/content", new Response(200, "application/octet-stream", content));

        byte[] read = client.attachment(base, new Access("t", null), "m 1", "a/1", 1024);

        assertThat(read).isEqualTo(content);
        assertThat(lastHeaders).containsEntry("accept", "application/octet-stream");
    }

    @Test
    void anAttachmentOverItsLimitIsRefusedAsTooLarge() {
        routes.put("/REST/ebox/ebox/messages/m-1/attachments/a-1/content", new Response(200, "application/pdf", new byte[4096]));

        assertThatThrownBy(() -> client.attachment(base, new Access("t", null), "m-1", "a-1", 1024))
                .isInstanceOfSatisfying(EboxResourceException.class, refused -> assertThat(refused.status).isEqualTo(413));
    }

    @Test
    void aMissingBodyIsEmpty() {
        assertThat(client.body(base, new Access("t", null), "m-1")).isEmpty();
        json("/ebox/messages/m-1/body", "{\"fr\":\"Bonjour\"}");
        assertThat(client.body(base, new Access("t", null), "m-1")).get().extracting(node -> node.path("fr").asString()).isEqualTo("Bonjour");
    }

    @Test
    void statusesAreClassifiedWithoutEchoingTheServiceOrTheToken() {
        routes.put("/REST/ebox/messageRegistries", new Response(401, "application/problem+json", "{}".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> client.registries(base, new Access("secret-token", null))).isInstanceOf(EboxUnauthorizedException.class);

        routes.put("/REST/ebox/messageRegistries", new Response(503, "text/plain", "maintenance secret-token".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> client.registries(base, new Access("secret-token", null)))
                .isInstanceOf(EboxTransportException.class)
                .hasMessage("ebox answered HTTP 503");

        routes.put("/REST/ebox/messageRegistries", new Response(200, "application/json", "[1,2]".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> client.registries(base, new Access("t", null))).isInstanceOf(EboxTransportException.class);

        assertThatThrownBy(() -> client.attachment(base, new Access("t", null), "m-9", "a-9", 10))
                .isInstanceOfSatisfying(EboxResourceException.class, refused -> assertThat(refused.status).isEqualTo(404));
    }

    @Test
    void aPrivateAddressIsRefusedOutsideTheLocalEnvironment() {
        json("/messageRegistries", "{\"items\":[]}");

        assertThatThrownBy(() -> client(false).registries(base, new Access("secret-token", null)))
                .isInstanceOf(EboxTransportException.class)
                .hasMessageNotContaining("secret-token");
        assertThat(lastHeaders).isEmpty();
    }
}
