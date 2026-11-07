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
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
 * Issue #19 (TODO §123) — l'exemple « autorisation riverain » (variante sans portail) sur ses fichiers : quand l'agent demande
 * des informations complémentaires, le demandeur reçoit un e-mail rendu depuis le gabarit {@code demande-informations-riverain},
 * qui cite la motivation saisie à l'instruction (texte et HTML). Le message passe par l'outbox réelle, le relais planifié et
 * un serveur SMTP en mémoire.
 */
@SpringBootTest(properties = "flowable.async-executor-activate=false")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class RiverainInformationRequestExampleIT {

    private static final Path EXAMPLE = Path.of("exemples/autorisation-riverain");
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String TENANT = "tenant_dev";
    private static final String AGENT = "agent-riverain";
    private static final int SMTP_PORT = freePort();
    private static final String MOTIVATION = "Merci de fournir le certificat d'immatriculation <en cours de validité> & une preuve de domicile.";

    @RegisterExtension
    static final GreenMailExtension MAIL = new GreenMailExtension(new ServerSetup(SMTP_PORT, "127.0.0.1", "smtp"));

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private ManagementService managementService;

    @Test
    void theApplicantReceivesTheMotivationTypedAtTheInstruction() throws Exception {
        configureTheExample();
        ObjectNode request = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/exemple-dossier.json").toFile());
        request.remove("demarrageWorkflow");
        String recipient = "demandeur-" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
        ((ObjectNode) request.get("fields")).put("email", recipient).put("plaque", "1-GAB-" + (100 + (int) (Math.random() * 800)));
        String dossierId = JSON.readTree(mockMvc.perform(post("/api/dossiers").with(agent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        String reference = JSON.readTree(mockMvc.perform(get("/api/dossiers/{id}", dossierId).with(agent()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("reference").asString();
        mockMvc.perform(patch("/api/dossiers/{id}/status", dossierId).with(agent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUVERT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId).with(agent()).with(csrf())
                        .header("Idempotency-Key", "riv-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processDefinitionKey\":\"autorisationRiverain\"}"))
                .andExpect(status().isCreated());
        String instance = runtimeService.createProcessInstanceQuery()
                .variableValueEquals("dossierId", dossierId).singleResult().getId();

        // L'agent demande des informations et saisit la motivation (obligatoire dans le questionnaire d'instruction).
        Task instruction = taskService.createTaskQuery().processInstanceId(instance).taskDefinitionKey("instruction").singleResult();
        mockMvc.perform(post("/api/workflow-tasks/{taskId}/complete", instruction.getId()).with(agent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("variables",
                                Map.of("decisionAgent", "DEMANDER_INFOS", "motivation", MOTIVATION)))))
                .andExpect(status().isNoContent());
        // L'e-mail est asynchrone : l'exécuteur de jobs est coupé, le test exécute le job.
        managementService.executeJob(managementService.createJobQuery().processInstanceId(instance).singleResult().getId());

        MimeMessage message = onlyMessage(recipient);
        assertThat(message.getSubject()).isEqualTo("Votre demande d'autorisation riverain " + reference + " : informations complémentaires");
        assertThat(message.getHeader("Auto-Submitted")).containsExactly("auto-generated");
        List<BodyPart> parts = leafParts(message);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).getContentType()).startsWith("text/plain");
        assertThat((String) parts.get(0).getContent()).contains(reference).contains(MOTIVATION)
                .contains("Sans réponse de votre part dans les 30 jours");
        assertThat(parts.get(1).getContentType()).startsWith("text/html");
        assertThat((String) parts.get(1).getContent()).contains("<b>" + reference + "</b>")
                .contains("Merci de fournir le certificat d'immatriculation &lt;en cours de validité&gt; &amp; une preuve de domicile.");
        // Le dossier attend les compléments.
        assertThat(taskService.createTaskQuery().processInstanceId(instance).taskDefinitionKey("complement").count()).isEqualTo(1);
    }

    /** Type, questionnaires, configuration, modèle de document, gabarit de notification et BPMN, tels que livrés dans l'exemple. */
    private void configureTheExample() throws Exception {
        RequestPostProcessor admin = user("admin-riverain", "TENANT_ADMIN");
        mockMvc.perform(post("/api/dossier-types").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(EXAMPLE.resolve("config/type-dossier.json"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossier-types/{code}/acl", "AUTORISATION_RIVERAIN").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\":\"" + AGENT + "\",\"permission\":\"EDIT\"}"))
                .andExpect(status().isNoContent());
        for (JsonNode schema : JSON.readTree(EXAMPLE.resolve("config/questions.json").toFile())) {
            // Le fichier suit la forme de l'outil MCP (fields) ; l'API REST attend fieldDefinitions.
            Map<String, Object> body = Map.of("schemaKey", schema.get("schemaKey").asString(),
                    "label", schema.get("label").asString(), "fieldDefinitions", schema.get("fields"));
            mockMvc.perform(post("/api/task-question-schemas").with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                    .andExpect(status().isCreated());
        }
        // Forme MCP (processDefinitionKey) ; l'API REST attend workflowKey.
        ObjectNode config = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/workflow-config.json").toFile());
        config.set("workflowKey", config.remove("processDefinitionKey"));
        mockMvc.perform(post("/api/workflow-type-configs").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(config)))
                .andExpect(status().isCreated());
        JsonNode document = JSON.readTree(EXAMPLE.resolve("config/modele-document.json").toFile());
        String documentId = JSON.readTree(mockMvc.perform(post("/api/document-templates").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "templateKey", document.get("templateKey").asString(),
                                "label", document.get("label").asString(),
                                "htmlContent", Files.readString(EXAMPLE.resolve("template-decision.html"))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(post("/api/document-templates/{id}/activate", documentId).with(admin).with(csrf()))
                .andExpect(status().isOk());
        // Le gabarit de notification : le fichier de l'exemple est le corps de POST /api/notification-templates.
        String templateId = JSON.readTree(mockMvc.perform(post("/api/notification-templates").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(EXAMPLE.resolve("config/gabarit-demande-informations.json"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(post("/api/notification-templates/{id}/activate", templateId).with(admin).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file",
                                "autorisation-riverain-sans-portail.bpmn20.xml", "application/xml",
                                Files.readAllBytes(EXAMPLE.resolve("autorisation-riverain-sans-portail.bpmn20.xml"))))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
    }

    private MimeMessage onlyMessage(String recipient) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (mine(recipient).isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        Thread.sleep(1_000); // laisse arriver un éventuel doublon
        assertThat(mine(recipient)).as("e-mail reçu par " + recipient).hasSize(1);
        return mine(recipient).getFirst();
    }

    private static List<MimeMessage> mine(String recipient) throws Exception {
        List<MimeMessage> mine = new ArrayList<>();
        for (MimeMessage message : MAIL.getReceivedMessages()) {
            if (message.getAllRecipients()[0].toString().equals(recipient)) {
                mine.add(message);
            }
        }
        return mine;
    }

    private static List<BodyPart> leafParts(MimeMessage message) throws Exception {
        List<BodyPart> leaves = new ArrayList<>();
        collect((Multipart) message.getContent(), leaves);
        return leaves;
    }

    private static void collect(Multipart multipart, List<BodyPart> leaves) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) {
                collect(nested, leaves);
            } else {
                leaves.add(part);
            }
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RequestPostProcessor agent() {
        return user(AGENT, "DOSSIER_MANAGER");
    }

    private static RequestPostProcessor user(String subject, String role) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
