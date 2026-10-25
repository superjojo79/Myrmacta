package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §87 (D3d) — client REST Service Bus contre un courtier simulé : verrouillage de lecture, terminé, abandon, bornes. */
class HttpServiceBusClientTest {

    private static final String AUTH = "SharedAccessSignature sr=x&sig=SECRET-SIG&se=1&skn=k";

    private HttpServer server;
    private final List<String> calls = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String start(java.util.function.Consumer<HttpServer> routes) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        routes.accept(server);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body, String... headers) throws IOException {
        for (int i = 0; i < headers.length; i += 2) {
            ex.getResponseHeaders().add(headers[i], headers[i + 1]);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    /** Un courtier qui sert les messages donnés puis une file vide ; chaque verrou est {@code base/…/messages/<id>/lock}. */
    private String broker(List<String> bodies, String hostForLocks) throws IOException {
        List<String> remaining = new ArrayList<>(bodies);
        return start(s -> s.createContext("/queue1/messages", ex -> {
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            String path = ex.getRequestURI().getPath();
            calls.add(ex.getRequestMethod() + " " + path);
            if (ex.getRequestMethod().equals("POST") && path.endsWith("/head")) {
                if (remaining.isEmpty()) {
                    reply(ex, 204, "");
                    return;
                }
                String body = remaining.remove(0);
                String id = "id" + (bodies.size() - remaining.size());
                String host = hostForLocks != null ? hostForLocks : "http://127.0.0.1:" + server.getAddress().getPort();
                reply(ex, 201, body, "Location", host + "/queue1/messages/" + id + "/lock",
                        "BrokerProperties", "{\"MessageId\":\"" + id + "\",\"DeliveryCount\":2}");
            } else {
                reply(ex, 200, "");
            }
        }));
    }

    private ServiceBusPort.Endpoint endpoint(String base) {
        return new ServiceBusPort.Endpoint(base, "queue1", () -> AUTH);
    }

    @Test
    void receivesInPeekLockModeThenCompletesAndAbandonsByTheLockAddress() throws Exception {
        String base = broker(List.of("{\"a\":1}", "{\"a\":2}"), null);
        var client = new HttpServiceBusClient(json);

        var messages = client.receive(endpoint(base), 10, 1, 1_000_000);

        assertThat(messages).extracting(ServiceBusPort.Received::body).containsExactly("{\"a\":1}", "{\"a\":2}");
        assertThat(messages.getFirst().messageId()).isEqualTo("id1");
        assertThat(messages.getFirst().deliveryCount()).isEqualTo(2);
        client.complete(endpoint(base), messages.get(0));
        client.abandon(endpoint(base), messages.get(1));
        assertThat(calls).contains("DELETE /queue1/messages/id1/lock", "PUT /queue1/messages/id2/lock");
        assertThat(authHeaders).allMatch(AUTH::equals);
    }

    @Test
    void theNumberOfMessagesPerPollIsBounded() throws Exception {
        String base = broker(List.of("1", "2", "3", "4"), null);
        assertThat(new HttpServiceBusClient(json).receive(endpoint(base), 2, 1, 1_000_000)).hasSize(2);
    }

    @Test
    void aLockAddressOnAnotherHostIsRefused_aCompromisedBrokerCannotRedirectOurCalls() throws Exception {
        String base = broker(List.of("{}"), "http://evil.example:9999");
        assertThatThrownBy(() -> new HttpServiceBusClient(json).receive(endpoint(base), 1, 1, 1_000_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("lock address");
    }

    @Test
    void anOversizedMessageIsHandedBackAndNeverReturned() throws Exception {
        String base = broker(List.of("x".repeat(5_000), "{\"ok\":true}"), null);
        var messages = new HttpServiceBusClient(json).receive(endpoint(base), 5, 1, 1_000);
        assertThat(messages).extracting(ServiceBusPort.Received::body).containsExactly("{\"ok\":true}");
        assertThat(calls).contains("PUT /queue1/messages/id1/lock"); // le gros message est abandonné
    }

    @Test
    void anErrorStatusNeverLeaksTheAuthorizationNorTheBody() throws Exception {
        String base = start(s -> s.createContext("/queue1/messages", ex -> reply(ex, 401, "bad token " + AUTH)));
        assertThatThrownBy(() -> new HttpServiceBusClient(json).receive(endpoint(base), 1, 1, 1_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("401")
                .hasMessageNotContaining("SECRET-SIG").hasMessageNotContaining("bad token");
        assertThat(endpoint(base).toString()).doesNotContain("SECRET").doesNotContain("Signature");
    }

    @Test
    void aRedirectIsNeverFollowed() throws Exception {
        String base = start(s -> s.createContext("/queue1/messages", ex -> reply(ex, 307, "", "Location", "http://127.0.0.1:1/x")));
        assertThatThrownBy(() -> new HttpServiceBusClient(json).receive(endpoint(base), 1, 1, 1_000)).hasMessageContaining("307");
    }

    @Test
    void endToEnd_theConnectorTurnsBrokerMessagesIntoEventsAndCompletesThem() throws Exception {
        String base = broker(List.of("{\"vendor\":{\"name\":\"ACME\"}}", "pas du json"), null);
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        var connector = new AzureServiceBusConnector(new SimpleJsonPathMapper(), guard, json,
                new ConnectorProperties(null, null, null, null, null, null), InboundConnectorProperties.defaults(),
                new HttpServiceBusClient(json), Clock.systemUTC());
        var instance = new ConnectorInstanceData(UUID.randomUUID(), "t1", "Bus", "azure-service-bus", true, Map.of(
                "namespaceHost", "contoso.servicebus.windows.net", "entityPath", "queue1", "sasKeyName", "k", "sasKey", "a2V5",
                "processKey", "p", "dossierTypeCode", "T", "mapping", Map.of("fields", Map.of("nom", "$.vendor.name"))));

        var events = new ArrayList<be.myrmacta.domain.connector.model.WorkflowTriggerEvent>();
        connector.process(instance, endpoint(base), events::add);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().dossierFields()).containsEntry("nom", "ACME");
        assertThat(calls).contains("DELETE /queue1/messages/id1/lock", "PUT /queue1/messages/id2/lock");
    }

    /** Revue D3c/D3d, M3 : une erreur en cours de lecture rend les messages déjà verrouillés au lieu de les laisser expirer. */
    @Test
    void anErrorMidWayGivesBackTheMessagesAlreadyLocked() throws Exception {
        java.util.concurrent.atomic.AtomicInteger heads = new java.util.concurrent.atomic.AtomicInteger();
        String base = start(s -> s.createContext("/queue1/messages", ex -> {
            String path = ex.getRequestURI().getPath();
            calls.add(ex.getRequestMethod() + " " + path);
            if (ex.getRequestMethod().equals("POST") && path.endsWith("/head")) {
                int n = heads.incrementAndGet();
                if (n == 1) {
                    String host = "http://127.0.0.1:" + server.getAddress().getPort();
                    reply(ex, 201, "{}", "Location", host + "/queue1/messages/id1/lock", "BrokerProperties", "{\"MessageId\":\"id1\"}");
                } else {
                    reply(ex, 500, "");
                }
            } else {
                reply(ex, 200, "");
            }
        }));
        assertThatThrownBy(() -> new HttpServiceBusClient(json).receive(endpoint(base), 5, 1, 1_000_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("500");
        assertThat(calls).contains("PUT /queue1/messages/id1/lock");
    }
}
