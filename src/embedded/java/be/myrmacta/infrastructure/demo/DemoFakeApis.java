package be.myrmacta.infrastructure.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * ({@link #OUTAGE_DOMAIN}) et les pannes commandées.
 * <p>
 * TODO §136 (D-DEMO-FAKE-FAULTS, issue #57) — une panne se commande sur une étape ({@link #FAULT_STEPS}), avec un statut
 * HTTP en erreur, pour un nombre d'appels borné ; l'étape répond ensuite de nouveau en succès. Route de pilotage, sur la
 * même écoute de la boucle locale : {@code POST /_demo/faults} (corps {@code {"step":"iam","status":503,"times":1}}) arme,
 * {@code DELETE /_demo/faults} désarme tout. Un connecteur ne l'atteint pas : ses chemins restent sous {@code /sirh},
 * {@code /iam} ou {@code /itsm}. Les compensations ({@code DELETE}) ne sont jamais en panne.
 * Le port est fixe par défaut ({@code app.demo.fake-apis.port}, 18090) : les connecteurs installés gardent leur adresse
 * d'un démarrage à l'autre. Build {@code embedded-db} seulement.
 */
@Component
@Profile({"demo", OnboardingDemoPack.VIDEO_PROFILE})
public class DemoFakeApis implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DemoFakeApis.class);

    /**
     * Le faux SIRH coupe la connexion quand l'adresse personnelle est dans ce domaine réservé (RFC 2606) : l'appel du
     * connecteur échoue, et ses relances épuisées, le job devient un incident rejouable (paquet « états »).
     */
    static final String OUTAGE_DOMAIN = ".invalid";

    /** Corps lu au plus : un client qui envoie sans fin ne remplit pas le tas. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    /** Journal des requêtes reçues : les plus anciennes sortent quand le plafond est atteint. */
    static final int MAX_RECEIVED = 256;

    /** Étapes dont on peut commander la panne : création SIRH, création IAM, groupe IAM, ticket ITSM. */
    static final Set<String> FAULT_STEPS = Set.of("sirh", "iam", "groups", "itsm");

    /** Appels en erreur au plus, par commande. */
    static final int MAX_FAULT_TIMES = 10;

    private static final String FAULTS_PATH = "/_demo/faults";

    private final int port;
    private final ObjectMapper json;
    private final List<String> received = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger sequence = new AtomicInteger(1000);
    /** Pannes armées, par étape ; protégées par {@code synchronized (faults)}. */
    private final Map<String, Fault> faults = new HashMap<>();
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

    /** Adresse d'écoute : toujours la boucle locale (D-DEMO-FAKE-BOUNDS). */
    InetSocketAddress listenAddress() {
        HttpServer current = server;
        if (current == null) {
            throw new IllegalStateException("demo: fake APIs not started");
        }
        return current.getAddress();
    }

    /** Les requêtes reçues, « MÉTHODE /chemin », dans l'ordre. */
    public List<String> received() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    /**
     * Les {@code times} prochains appels de l'étape répondent {@code status}. Remplace une panne déjà armée sur l'étape.
     *
     * @throws IllegalArgumentException étape inconnue, statut hors 400-599, nombre hors 1-{@link #MAX_FAULT_TIMES}
     */
    public void arm(String step, int status, int times) {
        if (!FAULT_STEPS.contains(step)) {
            throw new IllegalArgumentException("unknown step: " + step + " (expected one of " + FAULT_STEPS + ")");
        }
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("status must be an HTTP error (400-599): " + status);
        }
        if (times < 1 || times > MAX_FAULT_TIMES) {
            throw new IllegalArgumentException("times must be between 1 and " + MAX_FAULT_TIMES + ": " + times);
        }
        synchronized (faults) {
            faults.put(step, new Fault(status, times));
        }
        log.info("demo: fake {} answers {} to its next {} call(s)", step, status, times);
    }

    /** Désarme toutes les pannes commandées. */
    public void clearFaults() {
        synchronized (faults) {
            faults.clear();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath();
            String line = exchange.getRequestMethod() + " " + path;
            byte[] body = readBody(exchange.getRequestBody());
            if (path.equals(FAULTS_PATH)) {
                send(exchange, control(exchange.getRequestMethod(), body));
                return;
            }
            synchronized (received) {
                received.add(line);
                while (received.size() > MAX_RECEIVED) {
                    received.remove(0);
                }
            }
            Response response = respond(line, body);
            if (response == Response.DROP) {
                return; // fermé sans réponse : l'appelant voit une coupure réseau
            }
            send(exchange, response);
        }
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }

    /** Route de pilotage : {@code POST} arme une panne, {@code DELETE} les désarme toutes. */
    private Response control(String method, byte[] body) {
        if (method.equals("DELETE")) {
            clearFaults();
            return new Response(204, "");
        }
        if (!method.equals("POST")) {
            return new Response(405, "{\"error\":\"use POST or DELETE\"}");
        }
        JsonNode command;
        try {
            command = json.readTree(body);
        } catch (RuntimeException ex) {
            return new Response(400, "{\"error\":\"unreadable body\"}");
        }
        if (command == null || !command.path("step").isString() || !command.path("status").isInt()
                || !command.path("times").isInt()) {
            return new Response(400, "{\"error\":\"expected {step, status, times}\"}");
        }
        try {
            arm(command.get("step").asString(), command.get("status").asInt(), command.get("times").asInt());
            return new Response(204, "");
        } catch (IllegalArgumentException ex) {
            return new Response(400, json.createObjectNode().put("error", ex.getMessage()).toString());
        }
    }

    /** L'étape d'une requête de création ou de groupe, ou {@code null} (compensation, route inconnue). */
    static String step(String line) {
        if (line.equals("POST /sirh/employees")) {
            return "sirh";
        }
        if (line.equals("POST /iam/users")) {
            return "iam";
        }
        if (line.startsWith("PUT /iam/users/") && line.contains("/groups/")) {
            return "groups";
        }
        if (line.equals("POST /itsm/tickets")) {
            return "itsm";
        }
        return null;
    }

    /** Consomme un appel de la panne armée sur l'étape, s'il y en a une. */
    private Response fault(String step) {
        if (step == null) {
            return null;
        }
        synchronized (faults) {
            Fault fault = faults.get(step);
            if (fault == null) {
                return null;
            }
            if (fault.remaining() <= 1) {
                faults.remove(step);
            } else {
                faults.put(step, new Fault(fault.status(), fault.remaining() - 1));
            }
            return new Response(fault.status(), json.createObjectNode().put("error", "demo fault").put("step", step).toString());
        }
    }

    Response respond(String line, byte[] body) {
        Response faulty = fault(step(line));
        if (faulty != null) {
            return faulty;
        }
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

    /**
     * Au plus {@link #MAX_BODY_BYTES} sont gardés pour la réponse. Le reste est jeté (tampon fixe) jusqu'à la fin du corps :
     * sinon la requête suivante sur la même connexion HTTP est désalignée.
     */
    private static byte[] readBody(InputStream in) throws IOException {
        byte[] body = in.readNBytes(MAX_BODY_BYTES);
        byte[] sink = new byte[8192];
        while (in.read(sink) >= 0) {
            // jeter
        }
        return body;
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

    private record Fault(int status, int remaining) {
    }

    record Response(int status, String body) {
        /** Connexion fermée sans réponse. */
        static final Response DROP = new Response(-1, "");
    }
}
