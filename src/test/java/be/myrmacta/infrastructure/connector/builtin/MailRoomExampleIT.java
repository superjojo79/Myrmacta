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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
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
 * TODO §112 (issue #217, lot 1) — le paquet {@code exemples/digital-mail-room} sur l'application : un e-mail lu par le
 * connecteur IMAP de l'exemple devient un courrier numéroté (type {@code COURRIER}), ses pièces sont rangées, le tri démarre
 * au statut « à trier », et les en-têtes de fil parviennent au process. Seule la boîte aux lettres est simulée. Placé dans ce
 * paquet parce que {@link ImapMailbox} n'est pas public.
 */
@SpringBootTest
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
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anEmailBecomesANumberedMailItemAwaitingSorting() throws Exception {
        configureTheExample();
        var invoice = new InboundAttachment("facture-2026-117.pdf", "application/pdf", "%PDF-1.4 facture".getBytes(StandardCharsets.US_ASCII));
        var mail = new ImapMailbox.Mail("<f117@fournisseur.example>", "compta@fournisseur.example", "Fournisseur SA",
                "Facture 2026-117", Instant.parse("2026-10-30T08:15:00Z"), "Veuillez trouver notre facture en annexe.", false,
                "mx.example.com; dmarc=pass header.from=fournisseur.example", List.of(invoice), 0,
                MailThread.of("<devis-12@fournisseur.example>", "<devis-12@fournisseur.example>", ""));
        doAnswer(call -> {
            ImapMailbox.Handler handler = call.getArgument(3);
            assertThat(handler.handle(mail)).isEqualTo(ImapMailbox.Outcome.PROCESSED);
            return null;
        }).when(mailbox).process(any(), anyInt(), any(), any());

        triggerRuntime.onIncomingEvent(exampleConnector(), Map.of());

        ProcessInstance sorting = awaitSortingProcess();
        String dossierId = (String) runtimeService.getVariable(sorting.getId(), "dossierId");
        JsonNode dossier = read(get("/api/dossiers/{id}", dossierId));
        assertThat(dossier.get("dossierTypeCode").asString()).isEqualTo("COURRIER");
        assertThat(dossier.get("reference").asString()).contains("COURRIER");
        assertThat(dossier.get("fields").get("canal").asString()).isEqualTo("EMAIL");
        assertThat(dossier.get("fields").get("expediteur").asString()).isEqualTo("compta@fournisseur.example");
        assertThat(dossier.get("fields").get("objet").asString()).isEqualTo("Facture 2026-117");
        assertThat(read(get("/api/dossiers/{id}/elements", dossierId)).toString()).contains("facture-2026-117.pdf");

        assertThat(taskService.createTaskQuery().processInstanceId(sorting.getId()).taskDefinitionKey("trierCourrier").count())
                .isEqualTo(1);
        assertThat(read(get("/api/dossiers/{id}/workflows", dossierId)).get("content").get(0).get("status").asString())
                .isEqualTo("A_TRIER");
        assertThat(runtimeService.getVariables(sorting.getId()))
                .containsEntry("mailMessageId", "<f117@fournisseur.example>")
                .containsEntry("mailInReplyTo", "<devis-12@fournisseur.example>")
                .containsEntry("mailSenderAuthenticated", true);

        // Le registre du courrier : la liste des dossiers filtrée sur le type.
        assertThat(read(get("/api/dossiers").param("dossierTypeCode", "COURRIER")).get("content").toString())
                .contains(dossier.get("reference").asString());
    }

    /** Type, configuration du workflow et BPMN, tels que livrés dans l'exemple. */
    private void configureTheExample() throws Exception {
        RequestPostProcessor admin = user("admin-courrier", "TENANT_ADMIN");
        mockMvc.perform(post("/api/dossier-types").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(EXAMPLE.resolve("config/type-dossier.json"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossier-types/{code}/acl", "COURRIER").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\":\"" + OPERATOR + "\",\"permission\":\"EDIT\"}"))
                .andExpect(status().isNoContent());
        // Forme MCP (processDefinitionKey) ; l'API REST attend workflowKey.
        ObjectNode config = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/workflow-config.json").toFile());
        config.set("workflowKey", config.remove("processDefinitionKey"));
        mockMvc.perform(post("/api/workflow-type-configs").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(config)))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file", "tri-courrier.bpmn20.xml",
                                "application/xml", Files.readAllBytes(EXAMPLE.resolve("tri-courrier.bpmn20.xml"))))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
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

    private ProcessInstance awaitSortingProcess() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            ProcessInstance found = runtimeService.createProcessInstanceQuery().processDefinitionKey("triCourrier").singleResult();
            if (found != null) {
                return found;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("the sorting process did not start; outbox: "
                + jdbc.queryForList("select status, attempts, last_error from outbox_event"));
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
