package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TODO §113 (issue #217, lots 1 et 2b) — le paquet {@code exemples/digital-mail-room} sur l'application : un e-mail lu par
 * le connecteur IMAP de l'exemple devient un courrier numéroté (type {@code COURRIER}) avec ses pièces ; la table de routage
 * l'envoie vers un dossier métier lié dont la tâche va au service désigné, ou vers la file de tri du groupe COURRIER. Seule la boîte aux lettres est simulée. Placé dans ce
 * paquet parce que {@link ImapMailbox} n'est pas public.
 */
@SpringBootTest(properties = "flowable.async-executor-activate=false")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class MailRoomExampleIT {

    private static final Path EXAMPLE = Path.of("exemples/digital-mail-room");
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String TENANT = "tenant_dev";
    private static final String OPERATOR = "operateur-courrier";

    @MockitoBean private ImapMailbox mailbox;

    @Autowired private MockMvc mockMvc;
    @Autowired private ConnectorTriggerRuntime triggerRuntime;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private HistoryService historyService;
    @Autowired private ManagementService managementService;
    @Autowired private JdbcTemplate jdbc;

    private static boolean configured;

    @Test
    void anInvoiceFromAKnownSupplierIsRoutedToAccounting_inALinkedInvoiceDossier() throws Exception {
        configureTheExample();
        var invoice = new InboundAttachment("facture-2026-117.pdf", "application/pdf", "%PDF-1.4 facture".getBytes(StandardCharsets.US_ASCII));
        deliver(new ImapMailbox.Mail("<f117@fournisseur.example>", "compta@fournisseur.example", "Fournisseur SA",
                "Facture 2026-117", Instant.parse("2026-10-30T08:15:00Z"), "Veuillez trouver notre facture en annexe.", false,
                "mx.example.com; dmarc=pass header.from=fournisseur.example", List.of(invoice), 0));

        String mailId = awaitMailDossier("Facture 2026-117");
        JsonNode dossier = awaitRouted(mailId);
        assertThat(dossier.get("reference").asString()).contains("COURRIER");
        assertThat(dossier.get("fields").get("canal").asString()).isEqualTo("EMAIL");
        assertThat(dossier.get("fields").get("service").asString()).isEqualTo("COMPTABILITE");
        assertThat(dossier.get("fields").get("echeance").asString())
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(30).toString());
        assertThat(read(get("/api/dossiers/{id}/elements", mailId)).toString()).contains("facture-2026-117.pdf");
        assertThat(read(get("/api/dossiers/{id}/workflows", mailId)).get("content").get(0).get("status").asString())
                .isEqualTo("ROUTE");

        JsonNode relation = read(get("/api/dossiers/{id}/relations", mailId)).get(0);
        assertThat(relation.get("type").asString()).isEqualTo("LINKED");
        String invoiceId = relation.get("relatedDossier").get("id").asString();
        JsonNode business = read(get("/api/dossiers/{id}", invoiceId));
        assertThat(business.get("dossierTypeCode").asString()).isEqualTo("FACTURE");
        assertThat(business.get("label").asString()).isEqualTo("Facture 2026-117");
        assertThat(business.get("fields").get("expediteur").asString()).isEqualTo("compta@fournisseur.example");
        String invoiceProcess = runtimeService.createProcessInstanceQuery().processDefinitionKey("traiterFacture")
                .variableValueEquals("dossierId", invoiceId).singleResult().getId();
        Task task = taskService.createTaskQuery().processInstanceId(invoiceProcess).singleResult();
        assertThat(task.getAssignee()).isNull();
        assertThat(taskService.getIdentityLinksForTask(task.getId()))
                .anySatisfy(link -> assertThat(link.getGroupId()).isEqualTo("COMPTABILITE"));

        // Le registre du courrier : la liste des dossiers filtrée sur le type.
        assertThat(read(get("/api/dossiers").param("dossierTypeCode", "COURRIER")).get("content").toString())
                .contains(dossier.get("reference").asString());
    }

    @Test
    void anUnrecognisedMailGoesToTheSortingQueueOfTheMailRoomGroup() throws Exception {
        configureTheExample();
        deliver(new ImapMailbox.Mail("<x1@ailleurs.example>", "quelquun@ailleurs.example", "Inconnu", "Question diverse",
                Instant.parse("2026-10-30T09:00:00Z"), "Bonjour", false, "mx.example.com; dmarc=pass header.from=ailleurs.example",
                List.of(), 0, MailThread.of("<devis-12@fournisseur.example>", "<devis-12@fournisseur.example>", "")));

        String mailId = awaitMailDossier("Question diverse");
        ProcessInstance sorting = awaitSortingTask(mailId);
        Task task = taskService.createTaskQuery().processInstanceId(sorting.getId()).taskDefinitionKey("trierCourrier").singleResult();
        assertThat(task.getAssignee()).isNull();
        assertThat(taskService.getIdentityLinksForTask(task.getId()))
                .anySatisfy(link -> assertThat(link.getGroupId()).isEqualTo("COURRIER"));
        assertThat(read(get("/api/dossiers/{id}/workflows", mailId)).get("content").get(0).get("status").asString())
                .isEqualTo("A_TRIER");
        assertThat(read(get("/api/dossiers/{id}", mailId)).get("fields").get("service").asString()).isEqualTo("COURRIER");
        assertThat(runtimeService.getVariables(sorting.getId()))
                .containsEntry("routageResultat", "A_TRIER")
                .containsEntry("routageMotif", "aucune action de routage")
                .containsEntry("mailInReplyTo", "<devis-12@fournisseur.example>")
                .containsEntry("mailSenderAuthenticated", true);
        assertThat(read(get("/api/dossiers/{id}/relations", mailId))).isEmpty();
    }

    private void deliver(ImapMailbox.Mail mail) throws Exception {
        doAnswer(call -> {
            ImapMailbox.Handler handler = call.getArgument(3);
            assertThat(handler.handle(mail)).isEqualTo(ImapMailbox.Outcome.PROCESSED);
            return null;
        }).when(mailbox).process(any(), anyInt(), any(), any());
        triggerRuntime.onIncomingEvent(exampleConnector(), Map.of());
    }

    /** Groupes, types, configurations, table DMN et BPMN, tels que livrés dans l'exemple (une fois par contexte). */
    private void configureTheExample() throws Exception {
        if (configured) {
            return;
        }
        RequestPostProcessor admin = user("admin-courrier", "TENANT_ADMIN");
        for (JsonNode group : JSON.readTree(EXAMPLE.resolve("config/groupes.json").toFile())) {
            mockMvc.perform(post("/api/user-groups").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(group)))
                    .andExpect(status().isCreated());
        }
        for (String type : List.of("config/type-dossier.json", "config/type-facture.json")) {
            JsonNode body = JSON.readTree(EXAMPLE.resolve(type).toFile());
            mockMvc.perform(post("/api/dossier-types").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(body)))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/dossier-types/{code}/acl", body.get("code").asString()).with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectUserId\":\"" + OPERATOR + "\",\"permission\":\"EDIT\"}"))
                    .andExpect(status().isNoContent());
        }
        for (String workflow : List.of("config/workflow-config.json", "config/workflow-config-facture.json")) {
            // Forme MCP (processDefinitionKey) ; l'API REST attend workflowKey.
            ObjectNode config = (ObjectNode) JSON.readTree(EXAMPLE.resolve(workflow).toFile());
            config.set("workflowKey", config.remove("processDefinitionKey"));
            mockMvc.perform(post("/api/workflow-type-configs").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(config)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(multipart("/api/dmn-decision-deployments").file(new MockMultipartFile("file", "routage-courrier.dmn",
                                "application/xml", Files.readAllBytes(EXAMPLE.resolve("routage-courrier.dmn"))))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
        for (String bpmn : List.of("traiter-facture.bpmn20.xml", "tri-courrier.bpmn20.xml")) {
            mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file", bpmn,
                                    "application/xml", Files.readAllBytes(EXAMPLE.resolve(bpmn))))
                            .with(admin).with(csrf()))
                    .andExpect(status().isCreated());
        }
        configured = true;
    }

    /** Le connecteur de l'exemple, sur une adresse publique littérale (pas de résolution DNS dans le test). */
    @SuppressWarnings("unchecked")
    private static ConnectorInstanceData exampleConnector() throws Exception {
        JsonNode example = JSON.readTree(EXAMPLE.resolve("config/connecteur-imap.exemple.json").toFile());
        Map<String, Object> config = JSON.convertValue(example.get("config"), Map.class);
        config.put("host", "93.184.216.34");
        return new ConnectorInstanceData(UUID.randomUUID(), TENANT, example.get("name").asString(),
                example.get("connectorType").asString(), true, config);
    }

    /** Le courrier créé pour cet objet : le relais de l'outbox démarre le tri en arrière-plan, qui peut déjà être fini. */
    private String awaitMailDossier(String subject) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            var started = historyService.createHistoricProcessInstanceQuery().processDefinitionKey("triCourrier")
                    .variableValueEquals("mailSubject", subject).singleResult();
            if (started != null) {
                return (String) historyService.createHistoricVariableInstanceQuery().processInstanceId(started.getId())
                        .variableName("dossierId").singleResult().getValue();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("no mail dossier for " + subject + "; outbox: "
                + jdbc.queryForList("select status, attempts, last_error from outbox_event"));
    }

    /** Exécuteur asynchrone coupé : les jobs (première étape du tri) sont exécutés ici, dans l'ordre. */
    private void runJobs() {
        for (var job : managementService.createJobQuery().list()) {
            managementService.executeJob(job.getId());
        }
    }

    private JsonNode awaitRouted(String mailId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            runJobs();
            JsonNode dossier = read(get("/api/dossiers/{id}", mailId));
            if ("CLOTURE".equals(dossier.get("status").asString())) {
                return dossier;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("the mail was not routed; outbox: "
                + jdbc.queryForList("select status, attempts, last_error from outbox_event") + "; dead jobs: "
                + jdbc.queryForList("select exception_msg_ from act_ru_deadletter_job") + "; jobs: "
                + jdbc.queryForList("select retries_, exception_msg_, lock_owner_, lock_exp_time_ from act_ru_job") + "; executions: "
                + jdbc.queryForList("select act_id_, is_active_ from act_ru_execution") + "; engine jobs: "
                + managementService.createJobQuery().count() + "; suspended: "
                + jdbc.queryForList("select exception_msg_ from act_ru_suspended_job") + "; status: "
                + read(get("/api/dossiers/{id}", mailId)).get("status").asString() + "; workflows: "
                + read(get("/api/dossiers/{id}/workflows", mailId)).get("content"));
    }

    private ProcessInstance awaitSortingTask(String mailId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            runJobs();
            ProcessInstance found = runtimeService.createProcessInstanceQuery().processDefinitionKey("triCourrier")
                    .variableValueEquals("dossierId", mailId).singleResult();
            if (found != null && taskService.createTaskQuery().processInstanceId(found.getId()).count() == 1) {
                return found;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("the sorting task was not created");
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return JSON.readTree(mockMvc.perform(request.with(user(OPERATOR, "DOSSIER_MANAGER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor user(String subject, String role) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
