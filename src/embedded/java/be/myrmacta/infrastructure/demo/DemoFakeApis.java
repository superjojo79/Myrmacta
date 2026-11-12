package be.myrmacta.infrastructure.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §127 (D-DEMO-FAKE-APIS, lot 1b de #55) — faux SIRH, IAM et ITSM de la démo, sur la boucle locale (serveur HTTP du
 * JDK, comme {@code OnboardingOrchestrationExampleIT}). Les connecteurs du paquet onboarding y pointent
 * ({@code http://localhost:<port>/sirh}, {@code /iam}, {@code /itsm}) ; la garde SSRF l'accepte en profil {@code local}.
 * Chaque création rend un identifiant nouveau ; toute route connue répond avec succès, sauf la panne du SIRH
 * ({@link #OUTAGE_DOMAIN}) ; les réponses en erreur viendront avec #57.
 * Le port est fixe par défaut ({@code app.demo.fake-apis.port}, 18090) : les connecteurs installés gardent leur adresse
 * d'un démarrage à l'autre. Build {@code embedded-db} seulement.
 */
@Component
@Profile("demo")
public class DemoFakeApis implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DemoFakeApis.class);

    /**
     * Le faux SIRH coupe la connexion quand l'adresse personnelle est dans ce domaine réservé (RFC 2606) : l'appel du
     * connecteur échoue, et ses relances épuisées, le job devient un incident rejouable (paquet « états »).
     */
    static final String OUTAGE_DOMAIN = ".invalid";

    private final int port;
    private final ObjectMapper json;
    private final List<String> received = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger sequence = new AtomicInteger(1000);
    private volatile HttpServer server;

    public DemoFakeApis(@Value("${app.demo.fake-apis.port:18090}") int port, ObjectMapper json) {
        this.port = port;
        this.json = json;
    }

    @Override
    public void start() {
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            created.createContext("/", this::handle);
            created.start();
            server = created;
        } catch (IOException ex) {
            throw new UncheckedIOException("demo: fake SIRH/IAM/ITSM cannot listen on port " + port
                    + " (set APP_DEMO_FAKE_APIS_PORT)", ex);
        }
        log.info("demo: fake SIRH, IAM and ITSM listening on {}", baseUrl());
    }

    @Override
    public void stop() {
        HttpServer current = server;
        if (current != null) {
            current.stop(0);
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    /** {@code http://localhost:<port>} ; chaque application a son préfixe ({@code /sirh}, {@code /iam}, {@code /itsm}). */
    public String baseUrl() {
        HttpServer current = server;
        if (current == null) {
            throw new IllegalStateException("demo: fake APIs not started");
        }
        return "http://localhost:" + current.getAddress().getPort();
    }

    /** Les requêtes reçues, « MÉTHODE /chemin », dans l'ordre. */
    public List<String> received() {
        return List.copyOf(received);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String line = exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath();
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(line);
            Response response = respond(line, body);
            if (response == Response.DROP) {
                return; // fermé sans réponse : l'appelant voit une coupure réseau
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    Response respond(String line, byte[] body) {
        int n = sequence.incrementAndGet();
        if (line.equals("POST /sirh/employees")) {
            String email = text(body, "personalEmail");
            if (email != null && email.endsWith(OUTAGE_DOMAIN)) {
                return Response.DROP;
            }
            return new Response(201, "{\"id\":\"E-" + n + "\"}");
        }
        if (line.equals("POST /iam/users")) {
            String username = text(body, "username");
            return new Response(201, json.createObjectNode().put("id", "u-" + n)
                    .put("username", username == null ? "user" + n : username).toString());
        }
        if (line.equals("POST /itsm/tickets")) {
            return new Response(201, "{\"number\":\"RITM00" + n + "\"}");
        }
        if ((line.startsWith("PUT /iam/users/") && line.contains("/groups/")) || line.startsWith("DELETE /iam/users/")
                || line.startsWith("DELETE /sirh/employees/")) {
            return new Response(204, "");
        }
        return new Response(404, "{\"error\":\"unknown route\"}");
    }

    /** Un texte de premier niveau du corps JSON, ou {@code null} (absent, vide ou corps illisible). */
    private String text(byte[] body, String name) {
        try {
            JsonNode request = json.readTree(body);
            JsonNode value = request == null ? null : request.get(name);
            if (value != null && value.isString() && !value.asString().isBlank()) {
                return value.asString();
            }
        } catch (RuntimeException ignored) {
            // Corps illisible : pas de valeur.
        }
        return null;
    }

    record Response(int status, String body) {
        /** Connexion fermée sans réponse. */
        static final Response DROP = new Response(-1, "");
    }
}
