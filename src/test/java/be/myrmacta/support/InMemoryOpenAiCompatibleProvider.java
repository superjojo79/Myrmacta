package be.myrmacta.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * TODO §106 (issue #211, P10) — faux fournisseur compatible OpenAI ({@code POST /v1/chat/completions}) pour les tests
 * de la pré-vérification : réponses scriptables (contenu JSON du modèle, erreur HTTP, redirection) et requêtes reçues
 * enregistrées, pour vérifier ce qui part vraiment chez le fournisseur (aucune donnée sensible, aucun outil).
 */
public final class InMemoryOpenAiCompatibleProvider implements AutoCloseable {

    /** Requête reçue : en-têtes (noms en minuscules) et corps JSON brut. */
    public record Received(Map<String, List<String>> headers, String body) {
        public String header(String name) {
            List<String> values = headers.get(name.toLowerCase(java.util.Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.getFirst();
        }
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile Function<String, String> content = body -> "{\"summary\":\"\",\"findings\":[]}";
    private volatile int status = 200;
    private volatile String errorBody = "";
    private volatile String redirect;
    private volatile long promptTokens = 120;
    private volatile long completionTokens = 30;
    private volatile String finishReason = "stop";

    public InMemoryOpenAiCompatibleProvider() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
    }

    /** URL de base à donner à {@code app.precheck.base-url}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /** Contenu (texte JSON) que le modèle rend, éventuellement calculé d'après le corps de la requête. */
    public InMemoryOpenAiCompatibleProvider respond(Function<String, String> modelContent) {
        this.content = modelContent;
        this.status = 200;
        this.redirect = null;
        return this;
    }

    public InMemoryOpenAiCompatibleProvider respond(String modelContent) {
        return respond(body -> modelContent);
    }

    public InMemoryOpenAiCompatibleProvider fail(int httpStatus, String body) {
        this.status = httpStatus;
        this.errorBody = body;
        return this;
    }

    public InMemoryOpenAiCompatibleProvider redirectTo(String location) {
        this.redirect = location;
        return this;
    }

    public InMemoryOpenAiCompatibleProvider usage(long prompt, long completion) {
        this.promptTokens = prompt;
        this.completionTokens = completion;
        return this;
    }

    /** {@code finish_reason} rendu ({@code stop} par défaut ; {@code length} : plafond de sortie atteint). */
    public InMemoryOpenAiCompatibleProvider finishReason(String reason) {
        this.finishReason = reason;
        return this;
    }

    public List<Received> received() {
        return List.copyOf(received);
    }

    public void reset() {
        received.clear();
        finishReason = "stop";
        respond("{\"summary\":\"\",\"findings\":[]}");
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new java.util.HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT), values));
        received.add(new Received(Map.copyOf(headers), body));
        if (redirect != null) {
            exchange.getResponseHeaders().add("Location", redirect);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        if (status != 200) {
            send(exchange, status, errorBody);
            return;
        }
        String json = "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"created\":1761400000,\"model\":\"fake\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":" + quote(content.apply(body))
                + "},\"finish_reason\":\"" + finishReason + "\"}],\"usage\":{\"prompt_tokens\":" + promptTokens
                + ",\"completion_tokens\":"
                + completionTokens + ",\"total_tokens\":" + (promptTokens + completionTokens) + "}}";
        send(exchange, 200, json);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return out.append('"').toString();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
