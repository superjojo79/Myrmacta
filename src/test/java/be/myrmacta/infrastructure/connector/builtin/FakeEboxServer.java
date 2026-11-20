package be.myrmacta.infrastructure.connector.builtin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §150 — faux serveur eBox Enterprise en boucle locale : jeton OAuth v5, Document Provider Registry, un registre de
 * messages en 3.8 (liste filtrée par partition, pièces, corps, une action « act-1 » par message). Les réponses suivent les schémas publics (specs non versionnées).
 */
final class FakeEboxServer implements AutoCloseable {

    record Message(String id, String partition, String subject, String sender, String receipt, String fileName, byte[] pdf, String body) {}

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final HttpServer server;
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    final List<String> requests = new CopyOnWriteArrayList<>();
    final List<String> authorizations = new CopyOnWriteArrayList<>();
    /** Actions confirmées, {@code messageId/actionId}. */
    final List<String> executedActions = new CopyOnWriteArrayList<>();

    FakeEboxServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String base() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    String registryUrl() {
        return base() + "/REST/ebox/enterprise/federation/documentProviderRegistry/v1";
    }

    String tokenUrl() {
        return base() + "/REST/oauth/v5/token";
    }

    void add(Message message) {
        messages.add(message);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        requests.add(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query));
        if (path.equals("/REST/oauth/v5/token")) {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!form.contains("client_assertion=")) {
                send(exchange, 400, "application/json", "{\"error\":\"invalid_client\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(exchange, 200, "application/json",
                    "{\"access_token\":\"fake-ebox-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        if (!"Bearer fake-ebox-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            send(exchange, 401, "application/problem+json", "{}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (path.endsWith("/documentProviderRegistry/v1/messageRegistries")) {
            Map<String, Object> endpoint = Map.of("endpointId", "1", "url", base() + "/REST/ebox/enterprise/messageRegistry/v3",
                    "apiVersion", "3.8", "isTemporarilyDeactivated", false, "activationDate", "2020-01-01");
            json(exchange, Map.of("items", List.of(Map.of("id", "socsec_v3", "name", Map.of("fr", "ONSS"), "description",
                    Map.of("fr", "Registre ONSS"), "endpoints", List.of(endpoint))), "totalItems", 1));
            return;
        }
        String prefix = "/REST/ebox/enterprise/messageRegistry/v3/ebox/messages";
        if (path.equals(prefix)) {
            Map<String, String> params = params(query);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Message message : messages) {
                if (message.partition().equals(params.get("recipientPartition"))) {
                    items.add(view(message));
                }
            }
            json(exchange, Map.of("items", items, "totalItems", items.size()));
            return;
        }
        for (Message message : messages) {
            if (path.equals(prefix + "/" + message.id() + "/messageActions") && "GET".equals(exchange.getRequestMethod())) {
                send(exchange, 200, "application/json", JSON.writeValueAsBytes(List.of(Map.of("messageActionId", "act-1",
                        "isExecuted", executedActions.contains(message.id() + "/act-1")))));
                return;
            }
            if (path.equals(prefix + "/" + message.id() + "/messageActions/act-1") && "PATCH".equals(exchange.getRequestMethod())) {
                String patch = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (patch.contains("\"isExecuted\":true")) {
                    executedActions.add(message.id() + "/act-1");
                }
                send(exchange, 204, "application/json", new byte[0]);
                return;
            }
            if (path.equals(prefix + "/" + message.id() + "/attachments/a-1/content")) {
                send(exchange, 200, "application/octet-stream", message.pdf());
                return;
            }
            if (path.equals(prefix + "/" + message.id() + "/body")) {
                json(exchange, Map.of("fr", message.body()));
                return;
            }
        }
        send(exchange, 404, "application/problem+json", "{}".getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> view(Message message) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("messageId", message.id());
        view.put("subject", Map.of("fr", message.subject()));
        view.put("sender", Map.of("eboxType", "enterprise", "enterpriseNumber", message.sender()));
        view.put("recipient", Map.of("eboxType", "enterprise", "enterpriseNumber", "0212345678", "partition", message.partition()));
        view.put("senderApplicationId", "onss-cotisations");
        view.put("messageTypeId", "avis");
        view.put("receiptDate", message.receipt());
        view.put("expirationDate", "2027-12-31T00:00:00+01:00");
        view.put("isRead", false);
        view.put("hasBody", message.body() != null);
        view.put("hasMessageActions", false);
        view.put("attachments", List.of(Map.of("attachmentId", "a-1", "fileName", message.fileName(), "isMainContent", true, "mediaType",
                "application/pdf", "size", message.pdf().length / 1024.0)));
        return view;
    }

    private static Map<String, String> params(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    params.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return params;
    }

    private static void json(HttpExchange exchange, Object body) throws IOException {
        send(exchange, 200, "application/json", JSON.writeValueAsBytes(body));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
