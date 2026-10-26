package be.myrmacta.infrastructure.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.InMemoryOpenAiCompatibleProvider;
import be.myrmacta.support.OidcClientTestConfiguration;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §106 (issue #211, lot B, B7) — pré-vérification de bout en bout sur le vrai moteur, une vraie base et le faux
 * fournisseur compatible OpenAI : brique asynchrone, exécution rangée, « sensible nulle part » (requêtes et journaux),
 * panne en incident rejouable, décisions, relance par la file, export, refus au déploiement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class AiPrecheckIT {

    private static final String TENANT = "tenant_dev";
    private static final String KEY = "aiPrecheckSmoke";
    private static final String SECRET_NRN = "85.07.30-033.61";
    private static final String SECRET_PIECE = "Carte d'identité 592-1234567-89";
    private static final InMemoryOpenAiCompatibleProvider PROVIDER = new InMemoryOpenAiCompatibleProvider();
    private static final String ANSWER = """
            {"summary":"Adresse conforme.","findings":[
              {"kind":"OK","sourceType":"QUESTION","sourceRef":"adresse","message":"La preuve situe le domicile à Namur.",
               "confidence":0.9},
              {"kind":"MISSING","sourceType":"FIELD","sourceRef":"nrn","message":"Source inventée.","confidence":0.5}]}""";

    @DynamicPropertySource
    static void precheck(DynamicPropertyRegistry registry) {
        registry.add("app.precheck.enabled", () -> "true");
        registry.add("app.precheck.base-url", PROVIDER::baseUrl);
        registry.add("app.precheck.api-key", () -> "test-key");
        registry.add("app.precheck.model", () -> "fake-model");
        registry.add("app.precheck.queue-poll-delay", () -> "1h"); // le test pilote la file lui-même
        registry.add("flowable.async-executor-activate", () -> "false"); // et les jobs asynchrones
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RepositoryService repositoryService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private ManagementService managementService;
    @Autowired private TenantProperties tenantProperties;
    @Autowired private BpmnParserPort parser;
    @Autowired private DeployBpmnProcessUseCase deployUseCase;
    @Autowired private DossierRepository dossiers;
    @Autowired private DossierTypeRepository dossierTypes;
    @Autowired private DossierElementRepository elements;
    @Autowired private DocumentStoragePort storage;
    @Autowired private PrecheckRunRepository runs;
    @Autowired private PrecheckRunRelay relay;

    private String type;

    @BeforeEach
    void typeExists() throws Exception {
        PROVIDER.reset();
        type = "PRE" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        dossierTypes.save(new DossierType(UUID.randomUUID(), TENANT, type, "Riverain", KEY, true, false, List.of(
                new FieldDefinition("commune", "Commune", FieldType.STRING, false, null, null, null, false),
                new FieldDefinition("telephone", "Téléphone", FieldType.STRING, true, null, null, null, false),
                new FieldDefinition("nrn", "Numéro de registre national", FieldType.STRING, true, null, null, null, true)),
                null, false, null, List.of(),
                List.of(new PieceType("preuve", "Preuve de domicile", false),
                        new PieceType("identite", "Carte d'identité", true)),
                "admin", "admin"));
        grant("agent-" + type, "EDIT");
        grant("reader-" + type, "VIEW");
    }

    private static String bpmn(String attributes) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow sourceRef="start" targetRef="check"/>
                    <serviceTask id="check" flowable:delegateExpression="${aiPrecheckTask}" %s/>
                    <sequenceFlow sourceRef="check" targetRef="wait"/>
                    <userTask id="wait" name="Instruction"/>
                    <sequenceFlow sourceRef="wait" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY, attributes);
    }

    private static final String BLOCK = "flowable:async=\"true\""
            + " flowable:precheckPieces=\"preuve : obligatoire, lire&#10;identite : obligatoire\""
            + " flowable:precheckFields=\"commune\""
            + " flowable:precheckQuestions=\"adresse = L'adresse de la preuve est-elle dans la commune déclarée ?\""
            + " flowable:precheckRules=\"variable:doublons &gt; 0 =&gt; INCOHERENCE : Un dossier semblable existe.\""
            + " flowable:precheckResult=\"precheck\"";

    private String deploy() {
        byte[] bytes = bpmn(BLOCK).getBytes(StandardCharsets.UTF_8);
        deployUseCase.validate(bytes, Optional.empty()); // les mêmes contrôles que le déploiement réel
        parser.extractProcessDefinitionKey(bytes);
        return repositoryService.createDeployment().tenantId(tenantProperties.id())
                .addBytes(KEY + ".bpmn20.xml", bytes).deploy().getId();
    }

    private Dossier dossierWithPieces() {
        Dossier dossier = dossiers.save(new Dossier(UUID.randomUUID(), TENANT, type + "-" + System.nanoTime(), type,
                "Demande de carte riverain", DossierStatus.EN_COURS,
                Map.of("commune", "Namur", "nrn", SECRET_NRN), null, 0L, "agent", Instant.now(), null, null, null,
                null, null));
        piece(dossier, "preuve", "facture.txt", "Facture d'électricité — domicile : rue de Fer 1, 5000 Namur.");
        piece(dossier, "identite", "carte.txt", SECRET_PIECE);
        return dossier;
    }

    private void piece(Dossier dossier, String pieceKey, String label, String text) {
        String ref = storage.store(TENANT, "text/plain", text.getBytes(StandardCharsets.UTF_8));
        elements.save(new DossierElement(UUID.randomUUID(), dossier.id(), TENANT, ElementType.ATTACHMENT, label, ref,
                Map.of("contentType", "text/plain", PieceType.METADATA_KEY, pieceKey), "agent", Instant.now(), null));
    }

    private ProcessInstance start(Dossier dossier) {
        return runtimeService.startProcessInstanceByKeyAndTenantId(KEY, "p-" + UUID.randomUUID(),
                Map.of("dossierId", dossier.id().toString(), "doublons", 0), tenantProperties.id());
    }

    private Job job(ProcessInstance instance) {
        return managementService.createJobQuery().processInstanceId(instance.getId()).singleResult();
    }

    @Test
    void theBlockRecordsARunThatTheAgentReadsDecidesAndReruns() throws Exception {
        PROVIDER.respond(ANSWER);
        String deployment = deploy();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            Dossier dossier = dossierWithPieces();
            ProcessInstance instance = start(dossier);
            assertThat(runtimeService.getVariable(instance.getId(), "precheck")).isNull(); // asynchrone : pas encore lancée

            managementService.executeJob(job(instance).getId());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) runtimeService.getVariable(instance.getId(), "precheck");
            assertThat(result).containsEntry("status", "SUCCEEDED").containsEntry("missing", 1L)
                    .containsEntry("inconsistent", 0L);
            assertThat(runtimeService.createActivityInstanceQuery().processInstanceId(instance.getId())
                    .activityId("wait").unfinished().count()).isEqualTo(1);

            // Sensible nulle part : ni dans la requête au fournisseur, ni dans les journaux.
            assertThat(PROVIDER.received()).singleElement().satisfies(request -> {
                assertThat(request.header("authorization")).isEqualTo("Bearer test-key");
                assertThat(request.body()).contains("rue de Fer 1", "Namur", "response_format")
                        .doesNotContain(SECRET_NRN, "592-1234567-89", "carte.txt");
            });
            assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(m -> m.contains(SECRET_NRN) || m.contains("592-1234567-89"));

            String runId = result.get("runId").toString();
            PrecheckRun run = runs.findById(UUID.fromString(runId)).orElseThrow();
            assertThat(run.trigger()).isEqualTo(PrecheckRun.Trigger.WORKFLOW);
            assertThat(run.discarded()).isEqualTo(1);
            assertThat(run.inputSha256()).hasSize(64);
            assertThat(run.tokensIn()).isEqualTo(120);
            String modelFinding = run.findings().stream()
                    .filter(f -> f.origin().name().equals("MODEL")).findFirst().orElseThrow().id().toString();

            mockMvc.perform(get("/api/dossiers/{id}/prechecks", dossier.id()).with(user("reader-" + type, "DOSSIER_VIEWER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$[0].summary").value("Adresse conforme."))
                    .andExpect(jsonPath("$[0].promptVersion").value("v1"))
                    .andExpect(jsonPath("$[0].findings[?(@.origin=='MODEL')].sourceRef").value("adresse"))
                    .andExpect(jsonPath("$[0].findings[?(@.sourceRef=='telephone')].kind").value("MISSING"))
                    .andExpect(jsonPath("$[0].findings[?(@.sourceRef=='identite')]").isEmpty());

            String decision = "/api/dossiers/{id}/prechecks/{runId}/findings/{findingId}/decision";
            mockMvc.perform(put(decision, dossier.id(), runId, modelFinding).with(user("reader-" + type, "DOSSIER_VIEWER"))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(decision, dossier.id(), runId, modelFinding).with(user("agent-" + type))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"PEUT-ETRE\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put(decision, dossier.id(), runId, UUID.randomUUID()).with(user("agent-" + type))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put(decision, dossier.id(), runId, modelFinding).with(user("agent-" + type))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                    .andExpect(jsonPath("$.decidedBy").value("agent-" + type));

            mockMvc.perform(get("/api/dossiers/{id}/prechecks/{runId}/export", dossier.id(), runId)
                            .with(user("agent-" + type)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/dossiers/{id}/prechecks/{runId}/export", dossier.id(), runId)
                            .with(user("admin-" + type, "TENANT_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inputSha256").value(run.inputSha256()))
                    .andExpect(jsonPath("$.spec.fields[0]").value("commune"));

            // Relance : mise en file (202), une seule à la fois, traitée par le relais.
            mockMvc.perform(post("/api/dossiers/{id}/prechecks", dossier.id()).with(user("reader-" + type, "DOSSIER_VIEWER"))
                            .with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/dossiers/{id}/prechecks", dossier.id()).with(user("agent-" + type)).with(csrf()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.trigger").value("MANUAL"));
            mockMvc.perform(post("/api/dossiers/{id}/prechecks", dossier.id()).with(user("agent-" + type)).with(csrf()))
                    .andExpect(status().isConflict());

            // F5 : relance, décisions et export tracés dans le journal de sécurité, identifiants seulement.
            assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.startsWith("event=precheck_finding_decided") && m.contains("findingId=" + modelFinding)
                            && m.contains("from=- to=ACCEPTED"))
                    .anyMatch(m -> m.startsWith("event=precheck_log_exported") && m.contains("runId=" + runId))
                    .anyMatch(m -> m.startsWith("event=precheck_rerun_requested channel=rest"))
                    .noneMatch(m -> m.startsWith("event=precheck") && m.contains("Adresse"));

            PrecheckRun rerun = relay.relayOne().orElseThrow();
            assertThat(rerun.attempts()).isEqualTo(1);
            assertThat(relay.relayOne()).isEmpty();
            assertThat(runs.findById(rerun.id()).orElseThrow().status()).isEqualTo(PrecheckRun.Status.SUCCEEDED);
            assertThat(PROVIDER.received()).hasSize(2);
            mockMvc.perform(get("/api/dossiers/{id}/prechecks", dossier.id()).with(user("reader-" + type, "DOSSIER_VIEWER")))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].trigger").value("MANUAL"))
                    .andExpect(jsonPath("$[0].requestedBy").value("agent-" + type))
                    .andExpect(jsonPath("$[1].trigger").value("WORKFLOW"));
        } finally {
            root.detachAppender(logs);
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void aProviderFailureIsRecordedAndBecomesARetriableIncident() {
        PROVIDER.fail(503, "{\"error\":\"upstream detail that must not leak\"}");
        String deployment = deploy();
        try {
            Dossier dossier = dossierWithPieces();
            ProcessInstance instance = start(dossier);
            String jobId = job(instance).getId();

            assertThatThrownBy(() -> managementService.executeJob(jobId)).rootCause()
                    .hasMessageContaining("AI pre-check failed").hasMessageNotContaining("upstream detail");

            // HC5 : l'exécution en échec reste rangée malgré l'annulation de la transaction du job.
            assertThat(runs.listByDossier(dossier.id(), 5)).singleElement().satisfies(run -> {
                assertThat(run.status()).isEqualTo(PrecheckRun.Status.FAILED);
                assertThat(run.failure()).isEqualTo("the language model provider is unavailable");
                assertThat(run.findings()).isNotEmpty(); // constats déterministes gardés
            });
            assertThat(runtimeService.getVariable(instance.getId(), "precheck")).isNull();

            PROVIDER.respond(ANSWER);
            Job retry = managementService.createTimerJobQuery().processInstanceId(instance.getId()).singleResult();
            managementService.moveTimerToExecutableJob(retry.getId());
            managementService.executeJob(retry.getId());

            assertThat(runs.listByDossier(dossier.id(), 5)).extracting(PrecheckRun::status)
                    .containsExactly(PrecheckRun.Status.SUCCEEDED, PrecheckRun.Status.FAILED);
            assertThat(runtimeService.getVariable(instance.getId(), "precheck")).isNotNull();
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void theDeploymentRefusesWhatWouldReachTheModelUnsafely() {
        assertThatThrownBy(() -> deployUseCase.validate(bpmn(BLOCK.replace("precheckFields=\"commune\"",
                "precheckFields=\"nrn\"")).getBytes(StandardCharsets.UTF_8), Optional.empty()))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("sensitive field nrn");
        assertThatThrownBy(() -> deployUseCase.validate(bpmn(BLOCK.replace("identite : obligatoire",
                "identite : obligatoire, lire")).getBytes(StandardCharsets.UTF_8), Optional.empty()))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("sensitive piece type identite");
        assertThatThrownBy(() -> deployUseCase.validate(bpmn(BLOCK.replace("flowable:async=\"true\"", ""))
                .getBytes(StandardCharsets.UTF_8), Optional.empty()))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("asynchronous");
    }

    private void grant(String userId, String permission) throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", type)
                        .with(user("acl-" + type, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\"}"))
                .andExpect(status().isNoContent());
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        String[] effective = roles.length == 0 ? new String[] {"DOSSIER_MANAGER"} : roles;
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(java.util.Arrays.stream(effective)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
