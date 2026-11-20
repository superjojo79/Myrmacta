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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §155 — faux serveur d'envoi eBox Enterprise en boucle locale : jeton OAuth v5 (assertion exigée, scope noté),
 * e-Box Federation 1.4 ({@code eboxPreferences/search}), publication e-Box REST 3.8 ({@code POST /ebox/messages}, 409 sur une clé
 * d'idempotence déjà vue, comme la spec l'annonce) et Registered Mail 1.1 (recommandé, preuve de dépôt). Les réponses suivent les
 * schémas publics (specs non versionnées, licence sans redistribution).
 */
final class FakeEboxSenderServer implements AutoCloseable {

    /** Une requête reçue, corps compris (lu en ISO-8859-1 pour garder les octets). */
    record Received(String method, String path, String query, Map<String, String> headers, String body) {}

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Pattern CUSTOMER_REFERENCE = Pattern.compile("\"customerReference\":\"([^\"]+)\"");

    private final HttpServer server;
    final List<Received> requests = new CopyOnWriteArrayList<>();
    final List<String> scopes = new CopyOnWriteArrayList<>();
    /** BCE connus de la fédération et leur préférence {@code exclusivelyEbox}. */
    final Map<String, Boolean> preferences = new ConcurrentHashMap<>();
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, String> proofs = new ConcurrentHashMap<>();
    /** Statut imposé au prochain {@code POST /ebox/messages} (0 : comportement normal). */
    volatile int nextPublicationStatus;
    /** Délai imposé au prochain {@code POST /ebox/messages}, en millisecondes. */
    volatile long nextPublicationDelayMillis;
    /** Nombre de 401 à rendre avant d'accepter le jeton. */
    volatile int unauthorizedBeforeAccept;
    private int published;
    private int tokens;

    FakeEboxSenderServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
    }

    String base() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    String tokenUrl() {
        return base() + "/REST/oauth/v5/token";
    }

    String publicationUrl() {
        return base() + "/REST/ebox/enterprise/messageRegistry/v3";
    }

    String federationUrl() {
        return base() + "/REST/ebox/enterprise/federation/v1";
    }

    String registeredMailUrl() {
        return base() + "/REST/registeredMail/v1";
    }

    List<Received> requestsTo(String pathSuffix) {
        return requests.stream().filter(r -> r.path().endsWith(pathSuffix)).toList();
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.getFirst()));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
        requests.add(new Received(exchange.getRequestMethod(), path, query, headers, body));
        if (path.equals("/REST/oauth/v5/token")) {
            if (!body.contains("client_assertion=")) {
                send(exchange, 400, "{\"error\":\"invalid_client\"}");
                return;
            }
            Matcher scope = Pattern.compile("scope=([^&]+)").matcher(body);
            scopes.add(scope.find() ? URLDecoder.decode(scope.group(1), StandardCharsets.UTF_8) : "");
            tokens++;
            send(exchange, 200, "{\"access_token\":\"fake-send-token-" + tokens + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
            return;
        }
        String authorization = headers.getOrDefault("authorization", "");
        if (!authorization.startsWith("Bearer fake-send-token-") || unauthorizedBeforeAccept > 0) {
            if (unauthorizedBeforeAccept > 0) {
                unauthorizedBeforeAccept--;
            }
            send(exchange, 401, "{}");
            return;
        }
        if (path.equals("/REST/ebox/enterprise/federation/v1/eboxPreferences/search")) {
            Map<?, ?> search = JSON.readValue(body, Map.class);
            List<Map<String, Object>> found = new ArrayList<>();
            for (Object bce : (List<?>) search.get("eboxIdValueList")) {
                Boolean exclusive = preferences.get(String.valueOf(bce));
                if (exclusive != null) {
                    found.add(Map.of("eboxId", Map.of("eboxType", "ENTERPRISE", "eboxIdValue", bce), "exclusivelyEbox", exclusive));
                }
            }
            send(exchange, 200, JSON.writeValueAsString(found));
            return;
        }
        if (path.equals("/REST/ebox/enterprise/messageRegistry/v3/ebox/messages") && "POST".equals(exchange.getRequestMethod())) {
            long delay = nextPublicationDelayMillis;
            nextPublicationDelayMillis = 0;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            int forced = nextPublicationStatus;
            nextPublicationStatus = 0;
            if (forced != 0) {
                send(exchange, forced, "{\"title\":\"forced\",\"detail\":\"secret detail of the registry\"}");
                return;
            }
            String key = headers.get("idempotency-key");
            if (key != null && !seenKeys.add(key)) {
                send(exchange, 409, "{\"title\":\"Conflict\"}");
                return;
            }
            published++;
            send(exchange, 201, "{\"messageId\":\"ebox-msg-" + published + "\",\"expirationDate\":\"2027-11-20T00:00:00+01:00\"}");
            return;
        }
        if (path.equals("/REST/registeredMail/v1/proofsOfDispatch")) {
            String reference = URLDecoder.decode(query.replace("customerReference=", ""), StandardCharsets.UTF_8);
            String proof = proofs.get(reference);
            send(exchange, 200, "{\"items\":[" + (proof == null ? "" : proof) + "]}");
            return;
        }
        if (path.equals("/REST/registeredMail/v1/registeredMails")) {
            Matcher reference = CUSTOMER_REFERENCE.matcher(body);
            if (!reference.find()) {
                send(exchange, 400, "{}");
                return;
            }
            published++;
            String proof = JSON.writeValueAsString(Map.of("customerReference", reference.group(1), "messageId", "rm-" + published,
                    "type", "decision", "documents", List.of(Map.of("httpPartName", "mainContent", "downloadFileName", "decision.pdf",
                            "mediaType", "application/pdf")), "signature", "signed-proof-" + published));
            proofs.put(reference.group(1), proof);
            send(exchange, 200, proof);
            return;
        }
        send(exchange, 404, "{}");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", status >= 400 ? "application/problem+json" : "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
