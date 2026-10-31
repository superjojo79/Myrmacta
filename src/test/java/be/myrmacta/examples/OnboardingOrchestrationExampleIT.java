package be.myrmacta.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TODO §112 (issue #260) — l'exemple {@code exemples/onboarding-collaborateur} (type, questionnaires, configuration,
 * connecteurs, BPMN tels que livrés) rejoué par les API de l'application contre un faux SIRH, un faux IAM et un faux ITSM
 * locaux : requêtes construites dans le BPMN ({@code connectorRequest}), {@code baseUrl} et chemin encodé, instances
 * « service seul », validations humaines qui orientent le processus, incident réessayé puis abandonné, compensation par
 * {@code DELETE}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnboardingOrchestrationExampleIT {

    private static final Path EXAMPLE = Path.of("exemples/onboarding-collaborateur");
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String TENANT = "tenant_dev";
    private static final String RH = "rh-onboarding";
    private static final FakeApis APIS = new FakeApis();
    private static boolean configured;
    private static final Map<String, String> CONNECTORS = new HashMap<>();

    @DynamicPropertySource
    static void jobs(DynamicPropertyRegistry registry) {
        registry.add("flowable.async-executor-activate", () -> "false");
    }

    @BeforeAll
    static void startApis() throws IOException {
        APIS.start();
    }

    @AfterAll
    static void stopApis() {
        APIS.stop();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RuntimeService runtimeService;
    @Autowired private HistoryService historyService;
    @Autowired private TaskService taskService;
    @Autowired private ManagementService managementService;

    @BeforeEach
    void configure() throws Exception {
        if (!configured) {
            configured = true; // une seule tentative : un échec de configuration n'est pas rejoué par chaque test
            configureTheExample();
        }
        APIS.reset();
    }

    @Test
    @Order(1)
    void correctionThenApproval_provisionsTheThreeApplications_andTheItValidationAddsAGroup() throws Exception {
        String dossierId = createDossier(Map.of());
        String instance = start(dossierId);

        // Le manager renvoie la fiche aux RH : rien n'est appelé.
        complete(instance, "validationManager", Map.of("decisionManager", "CORRIGER", "commentaireManager", "Fonction"));
        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossierId).with(rh()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":{\"fonction\":\"Développeuse Java senior\"}}"))
                .andExpect(status().isOk());
        complete(instance, "correction", Map.of());
        assertThat(APIS.received()).isEmpty();

        // Il approuve : SIRH puis IAM, avec le matricule rendu par le SIRH.
        complete(instance, "validationManager", Map.of("decisionManager", "APPROUVER"));
        runJobs(instance);
        assertThat(waitingTask(instance)).isEqualTo("validationAcces");

        // L'IT accorde des accès étendus : ajout au groupe du profil, puis ticket matériel.
        complete(instance, "validationAcces", Map.of("niveauAcces", "ETENDU"));
        runJobs(instance);

        assertThat(APIS.received()).extracting(Received::line).containsExactly(
                "POST /sirh/employees",
                "POST /iam/users",
                "PUT /iam/users/u-7f3a/groups/developpeurs",
                "POST /itsm/tickets");
        Received sirh = APIS.received().get(0);
        assertThat(sirh.authorization()).isEqualTo("Bearer sirh-token");
        assertThat(sirh.contentType()).isEqualTo("application/json");
        JsonNode sirhBody = JSON.readTree(sirh.body());
        assertThat(sirhBody.get("lastName").asString()).isEqualTo("Van den Broeck");
        assertThat(sirhBody.get("jobTitle").asString()).isEqualTo("Développeuse Java senior");
        assertThat(sirhBody.get("startDate").asString()).isEqualTo("2026-12-01");
        JsonNode iamBody = JSON.readTree(APIS.received().get(1).body());
        assertThat(APIS.received().get(1).authorization()).isEqualTo("Bearer iam-token");
        assertThat(iamBody.get("employeeNumber").asString()).isEqualTo("E-1001");
        assertThat(iamBody.get("username").asString()).isEqualTo("évandenbroeck");
        JsonNode ticket = JSON.readTree(APIS.received().get(3).body());
        assertThat(ticket.get("requestedFor").asString()).isEqualTo("evdbroeck");
        assertThat(ticket.get("profile").asString()).isEqualTo("DEVELOPPEUR");

        JsonNode fields = dossier(dossierId).get("fields");
        assertThat(fields.get("matriculeSirh").asString()).isEqualTo("E-1001");
        assertThat(fields.get("identifiantIam").asString()).isEqualTo("u-7f3a");
        assertThat(fields.get("loginIam").asString()).isEqualTo("evdbroeck");
        assertThat(fields.get("accesEtendus").asBoolean()).isTrue();
        assertThat(fields.get("ticketMateriel").asString()).isEqualTo("RITM0012345");
        assertThat(dossier(dossierId).get("status").asString()).isEqualTo("CLOTURE");
        assertEnded(instance, "fin");
    }

    @Test
    @Order(2)
    void aFailedCallIsRetriedThenAbandoned_andWhatWasCreatedIsDeleted() throws Exception {
        String dossierId = createDossier(Map.of("profilPoste", "STANDARD"));
        String instance = start(dossierId);
        APIS.fail("POST /iam/users", 503, "{\"error\":\"maintenance\"}");
        APIS.fail("POST /itsm/tickets", 500, "{\"error\":\"catalogue indisponible\"}");

        complete(instance, "validationManager", Map.of("decisionManager", "APPROUVER"));
        runJobs(instance);

        // L'IAM répond 503 : incident noté au dossier, l'humain choisit de réessayer.
        assertThat(waitingTask(instance)).isEqualTo("traiterIncident");
        assertThat(dossier(dossierId).get("fields").get("dernierIncident").asString())
                .startsWith("IAM : HTTP 503").contains("maintenance");
        complete(instance, "traiterIncident", Map.of("actionIncident", "REESSAYER"));
        runJobs(instance);

        // Profil STANDARD : pas de validation IT. L'ITSM répond 500 ; l'humain abandonne.
        assertThat(waitingTask(instance)).isEqualTo("traiterIncident");
        assertThat(dossier(dossierId).get("fields").get("dernierIncident").asString()).startsWith("ITSM : HTTP 500");
        // Revue §112 (H1) : une personne qui a le droit EDIT change les identifiants affichés avant l'abandon ; la
        // compensation supprime quand même ce que le workflow a créé, pas ce que le dossier affiche.
        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossierId).with(rh()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":{\"identifiantIam\":\"u-admin\",\"matriculeSirh\":\"E-0001\"}}"))
                .andExpect(status().isOk());
        complete(instance, "traiterIncident", Map.of("actionIncident", "ABANDONNER"));
        runJobs(instance);

        assertThat(APIS.received()).extracting(Received::line).containsExactly(
                "POST /sirh/employees",
                "POST /iam/users",
                "POST /iam/users",
                "POST /itsm/tickets",
                "DELETE /iam/users/u-7f3a",
                "DELETE /sirh/employees/E-1001");
        assertThat(APIS.received().get(4).body()).isEmpty();
        assertThat(dossier(dossierId).get("fields").get("journalCompensation").asString())
                .isEqualTo("IAM supprimé : HTTP 204 ; SIRH supprimé : HTTP 204");
        assertThat(dossier(dossierId).get("status").asString()).isEqualTo("CLOTURE");
        assertEnded(instance, "finAnnule");
    }

    @Test
    @Order(3)
    void aRefusalCallsNoApplication() throws Exception {
        String dossierId = createDossier(Map.of());
        String instance = start(dossierId);

        complete(instance, "validationManager", Map.of("decisionManager", "REFUSER", "commentaireManager", "Doublon"));
        runJobs(instance);

        assertThat(APIS.received()).isEmpty();
        assertEnded(instance, "finRefus");
    }

    @Test
    @Order(4)
    void forgedIdentifiersInTheDossierOrTheStartVariablesNeverReachTheCompensation() throws Exception {
        // Revue §112 (H1) : identifiants saisis dans le dossier à la création et variables posées au démarrage.
        String dossierId = createDossier(Map.of("identifiantIam", "u-admin", "matriculeSirh", "E-0001"));
        String instance = start(dossierId, Map.of("compteIam", "u-admin", "matricule", "E-0001"));
        APIS.fail("POST /sirh/employees", 400, "{\"error\":\"personalEmail\"}");

        complete(instance, "validationManager", Map.of("decisionManager", "APPROUVER"));
        runJobs(instance);
        assertThat(waitingTask(instance)).isEqualTo("traiterIncident");
        complete(instance, "traiterIncident", Map.of("actionIncident", "ABANDONNER"));
        runJobs(instance);

        assertThat(APIS.received()).extracting(Received::line).containsExactly("POST /sirh/employees");
        assertEnded(instance, "finAnnule");
    }

    @Test
    @Order(5)
    void theServiceOnlyConnectorsHaveNoInboundWebhook() throws Exception {
        mockMvc.perform(post("/api/webhooks/{id}", CONNECTORS.get("SIRH")).header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- configuration de l'exemple

    /** Type, questionnaires, configuration, connecteurs et BPMN, tels que livrés dans l'exemple. */
    private void configureTheExample() throws Exception {
        RequestPostProcessor admin = user("admin-onboarding", "TENANT_ADMIN");
        mockMvc.perform(post("/api/dossier-types").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(EXAMPLE.resolve("config/type-dossier.json"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossier-types/{code}/acl", "ONBOARDING_COLLABORATEUR").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\":\"" + RH + "\",\"permission\":\"EDIT\"}"))
                .andExpect(status().isNoContent());
        for (JsonNode schema : JSON.readTree(EXAMPLE.resolve("config/questions.json").toFile())) {
            Map<String, Object> body = Map.of("schemaKey", schema.get("schemaKey").asString(),
                    "label", schema.get("label").asString(), "fieldDefinitions", schema.get("fields"));
            mockMvc.perform(post("/api/task-question-schemas").with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                    .andExpect(status().isCreated());
        }
        ObjectNode config = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/workflow-config.json").toFile());
        config.set("workflowKey", config.remove("processDefinitionKey"));
        mockMvc.perform(post("/api/workflow-type-configs").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(config)))
                .andExpect(status().isCreated());

        // Connecteurs « service seul » de l'exemple, pointés sur les faux services (en-tête statique pour les trois).
        String bpmn = Files.readString(EXAMPLE.resolve("onboarding-collaborateur.bpmn20.xml"));
        for (JsonNode connector : JSON.readTree(EXAMPLE.resolve("config/connecteurs.json").toFile())) {
            String name = connector.get("name").asString();
            ObjectNode connectorConfig = JSON.createObjectNode();
            connectorConfig.put("webhookEnabled", connector.get("config").get("webhookEnabled").asBoolean());
            connectorConfig.put("baseUrl", APIS.baseUrl() + "/" + name.toLowerCase());
            connectorConfig.put("authHeaderValue", "Bearer " + name.toLowerCase() + "-token");
            ObjectNode payload = JSON.createObjectNode();
            payload.put("name", "Onboarding " + name);
            payload.put("connectorType", connector.get("connectorType").asString());
            payload.put("enabled", true);
            payload.set("config", connectorConfig);
            String id = JSON.readTree(mockMvc.perform(post("/api/connectors").with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(payload)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
            CONNECTORS.put(name, id);
            bpmn = bpmn.replace(connector.get("_placeholder").asString(), id);
        }
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file",
                                "onboarding-collaborateur.bpmn20.xml", "application/xml",
                                bpmn.getBytes(StandardCharsets.UTF_8)))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
    }

    private String createDossier(Map<String, String> overrides) throws Exception {
        ObjectNode request = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/exemple-dossier.json").toFile());
        overrides.forEach(((ObjectNode) request.get("fields"))::put);
        String id = JSON.readTree(mockMvc.perform(post("/api/dossiers").with(rh()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(patch("/api/dossiers/{id}/status", id).with(rh()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUVERT\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String start(String dossierId) throws Exception {
        return start(dossierId, Map.of());
    }

    private String start(String dossierId, Map<String, Object> variables) throws Exception {
        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId).with(rh()).with(csrf())
                        .header("Idempotency-Key", "onb-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "processDefinitionKey", "onboardingCollaborateur", "variables", variables))))
                .andExpect(status().isCreated());
        String instance = runtimeService.createProcessInstanceQuery()
                .variableValueEquals("dossierId", dossierId).singleResult().getId();
        assertThat(waitingTask(instance)).isEqualTo("validationManager");
        return instance;
    }

    private void complete(String instance, String activity, Map<String, Object> variables) throws Exception {
        Task task = taskService.createTaskQuery().processInstanceId(instance).taskDefinitionKey(activity).singleResult();
        assertThat(task).as("task " + activity).isNotNull();
        mockMvc.perform(post("/api/workflow-tasks/{taskId}/complete", task.getId()).with(rh()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("variables", variables))))
                .andExpect(status().isNoContent());
    }

    /** Exécute les appels asynchrones (un job par appel de connecteur) jusqu'à la prochaine tâche humaine ou la fin. */
    private void runJobs(String instance) {
        for (int i = 0; i < 20; i++) {
            List<Job> jobs = managementService.createJobQuery().processInstanceId(instance).list();
            if (jobs.isEmpty()) {
                return;
            }
            managementService.executeJob(jobs.getFirst().getId());
        }
        throw new AssertionError("too many jobs");
    }

    private String waitingTask(String instance) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(instance).list();
        assertThat(tasks).hasSize(1);
        return tasks.getFirst().getTaskDefinitionKey();
    }

    private void assertEnded(String instance, String endEvent) {
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instance).count()).isZero();
        assertThat(historyService.createHistoricActivityInstanceQuery().processInstanceId(instance)
                .activityId(endEvent).count()).isEqualTo(1);
    }

    private JsonNode dossier(String id) throws Exception {
        return JSON.readTree(mockMvc.perform(get("/api/dossiers/{id}", id).with(rh()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor rh() {
        return user(RH, "DOSSIER_MANAGER");
    }

    private static RequestPostProcessor user(String subject, String role) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    // ---------------------------------------------------------------- faux SIRH, IAM et ITSM

    record Received(String method, String path, String authorization, String contentType, String body) {
        String line() {
            return method + " " + path;
        }
    }

    private record Response(int status, String body) {}

    /** Un serveur HTTP local qui joue les trois applications ; réponses par défaut, échecs programmables. */
    private static final class FakeApis {
        private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Deque<Response>> failures = new HashMap<>();
        private HttpServer server;

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        void stop() {
            server.stop(0);
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void reset() {
            received.clear();
            failures.clear();
        }

        void fail(String line, int status, String body) {
            failures.computeIfAbsent(line, k -> new ArrayDeque<>()).add(new Response(status, body));
        }

        List<Received> received() {
            return List.copyOf(received);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(method, path, exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"), body));
            String line = method + " " + path;
            Deque<Response> queued = failures.get(line);
            Response response = queued != null && !queued.isEmpty() ? queued.poll() : defaultResponse(line);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        }

        private static Response defaultResponse(String line) {
            return switch (line) {
                case "POST /sirh/employees" -> new Response(201, "{\"id\":\"E-1001\"}");
                case "POST /iam/users" -> new Response(201, "{\"id\":\"u-7f3a\",\"username\":\"evdbroeck\"}");
                case "POST /itsm/tickets" -> new Response(201, "{\"number\":\"RITM0012345\"}");
                case "PUT /iam/users/u-7f3a/groups/developpeurs", "DELETE /iam/users/u-7f3a",
                        "DELETE /sirh/employees/E-1001" -> new Response(204, "");
                default -> new Response(404, "{\"error\":\"unknown route\"}");
            };
        }
    }
}
