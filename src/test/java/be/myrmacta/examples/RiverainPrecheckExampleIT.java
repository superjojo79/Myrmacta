package be.myrmacta.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.InMemoryOpenAiCompatibleProvider;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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
 * TODO §106 (issue #211, lot D, D3) — scénario de la fiche (§6) sur les <b>fichiers de l'exemple</b>
 * {@code exemples/autorisation-riverain} (type, questionnaires, configuration, modèle, BPMN « preverification »), par les
 * API de l'application et avec le faux fournisseur : pièce manquante (déterministe), code postal incohérent avec la
 * commune (règle), questions au modèle et synthèse ; l'agent accepte deux constats et en écarte un ; le journal exporté
 * montre le modèle, la version du prompt et les choix. La pièce d'identité, sensible, n'atteint jamais le fournisseur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class RiverainPrecheckExampleIT {

    private static final Path EXAMPLE = Path.of("exemples/autorisation-riverain");
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String TENANT = "tenant_dev";
    private static final String AGENT = "agent-riverain";
    private static final String SECRET_ID = "592-1234567-89";
    private static final InMemoryOpenAiCompatibleProvider PROVIDER = new InMemoryOpenAiCompatibleProvider();
    private static final String ANSWER = """
            {"summary":"Preuve de domicile absente : domicile non vérifiable.\\nCertificat d'immatriculation conforme à la plaque déclarée.\\nCode postal à vérifier.","findings":[
              {"kind":"TO_CHECK","sourceType":"QUESTION","sourceRef":"domicileAuNom","message":"Aucune preuve de domicile fournie : titulaire non vérifiable.","confidence":0.9},
              {"kind":"TO_CHECK","sourceType":"QUESTION","sourceRef":"adresseConforme","message":"Adresse non vérifiable sans preuve de domicile.","confidence":0.85},
              {"kind":"OK","sourceType":"QUESTION","sourceRef":"plaqueConforme","message":"La plaque du certificat est 1-ABC-123, comme dans le formulaire.","confidence":0.95}]}""";

    @DynamicPropertySource
    static void precheck(DynamicPropertyRegistry registry) {
        registry.add("app.precheck.enabled", () -> "true");
        registry.add("app.precheck.base-url", PROVIDER::baseUrl);
        registry.add("app.precheck.api-key", () -> "test-key");
        registry.add("app.precheck.model", () -> "mistral-small-latest");
        registry.add("app.precheck.queue-poll-delay", () -> "1h");
        registry.add("flowable.async-executor-activate", () -> "false");
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RuntimeService runtimeService;
    @Autowired private ManagementService managementService;
    @Autowired private TenantProperties tenantProperties;

    @Test
    void theDemoScenarioOfTheFicheRunsOnTheExampleFiles() throws Exception {
        configureTheExample();
        PROVIDER.respond(ANSWER);

        // 1. Demande de l'exemple, sans preuve de domicile et avec un code postal d'une autre commune.
        ObjectNode request = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/exemple-dossier.json").toFile());
        ((ObjectNode) request.get("fields")).put("codePostal", "5000"); // Namur, alors que la commune est Bruxelles
        request.remove("demarrageWorkflow");
        String dossierId = JSON.readTree(mockMvc.perform(post("/api/dossiers").with(agent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        upload(dossierId, "certificatImmatriculation", "Certificat d'immatriculation",
                "Certificat d'immatriculation - Plaque : 1-ABC-123 - Titulaire : Camille Dupont");
        upload(dossierId, "pieceIdentite", "Carte d'identité", "Carte d'identité de Camille Dupont - n° " + SECRET_ID);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/dossiers/{id}/status", dossierId).with(agent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUVERT\"}"))
                .andExpect(status().isOk());

        // 2. Le workflow démarre ; la pré-vérification (asynchrone) s'exécute avant « Instruire la demande ».
        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId).with(agent()).with(csrf())
                        .header("Idempotency-Key", "riv-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processDefinitionKey\":\"autorisationRiverain\"}"))
                .andExpect(status().isCreated());
        String instance = runtimeService.createProcessInstanceQuery()
                .variableValueEquals("dossierId", dossierId).singleResult().getId();
        managementService.executeJob(managementService.createJobQuery().processInstanceId(instance).singleResult().getId());

        assertThat(runtimeService.createActivityInstanceQuery().processInstanceId(instance).activityId("instruction")
                .unfinished().count()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) runtimeService.getVariable(instance, "preverification");
        assertThat(result).containsEntry("status", "SUCCEEDED").containsEntry("missing", 1L)
                .containsEntry("inconsistent", 1L);
        assertThat(PROVIDER.received()).singleElement().satisfies(sent -> assertThat(sent.body())
                .contains("1-ABC-123", "Bruxelles", "domicileAuNom")
                .doesNotContain(SECRET_ID, "Carte d'identité de Camille"));

        // 3. L'agent ouvre la fiche : constats sourcés.
        JsonNode run = JSON.readTree(mockMvc.perform(get("/api/dossiers/{id}/prechecks", dossierId).with(agent()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get(0);
        assertThat(run.get("summary").asString()).contains("Preuve de domicile absente");
        List<String> findings = new ArrayList<>();
        for (JsonNode f : run.get("findings")) {
            findings.add(f.get("kind").asString() + " " + f.get("origin").asString() + " " + f.get("sourceRef").asString()
                    + " : " + f.get("message").asString());
        }
        assertThat(findings).containsExactly(
                "MISSING DETERMINISTIC preuveDomicile : Pièce manquante : Preuve de domicile.",
                "INCONSISTENT DETERMINISTIC codePostalIncoherent : Code postal incohérent avec la commune déclarée.",
                "TO_CHECK MODEL domicileAuNom : Aucune preuve de domicile fournie : titulaire non vérifiable.",
                "TO_CHECK MODEL adresseConforme : Adresse non vérifiable sans preuve de domicile.",
                "OK MODEL plaqueConforme : La plaque du certificat est 1-ABC-123, comme dans le formulaire.");

        // 4. Il accepte deux constats et en écarte un.
        String runId = run.get("id").asString();
        decide(dossierId, runId, run.get("findings").get(0), "ACCEPTED");
        decide(dossierId, runId, run.get("findings").get(1), "ACCEPTED");
        decide(dossierId, runId, run.get("findings").get(3), "DISMISSED");

        // 5. Le journal : modèle, version du prompt, constats et choix de l'agent.
        mockMvc.perform(get("/api/dossiers/{id}/prechecks/{runId}/export", dossierId, runId)
                        .with(user("admin-riverain", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.model").value("mistral-small-latest"))
                .andExpect(jsonPath("$.run.promptVersion").value("v1"))
                .andExpect(jsonPath("$.inputSha256").isString())
                .andExpect(jsonPath("$.run.findings[0].decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.run.findings[0].decidedBy").value(AGENT))
                .andExpect(jsonPath("$.run.findings[1].decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.run.findings[3].decision").value("DISMISSED"));
    }

    /** Type, questionnaires, configuration du workflow, modèle de document et BPMN, tels que livrés dans l'exemple. */
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
        JsonNode template = JSON.readTree(EXAMPLE.resolve("config/modele-document.json").toFile());
        String templateId = JSON.readTree(mockMvc.perform(post("/api/document-templates").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "templateKey", template.get("templateKey").asString(),
                                "label", template.get("label").asString(),
                                "htmlContent", Files.readString(EXAMPLE.resolve("template-decision.html"))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(post("/api/document-templates/{id}/activate", templateId).with(admin).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file",
                                "autorisation-riverain-preverification.bpmn20.xml", "application/xml",
                                Files.readAllBytes(EXAMPLE.resolve("autorisation-riverain-preverification.bpmn20.xml"))))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
        assertThat(tenantProperties.id()).isEqualTo(TENANT);
    }

    private void upload(String dossierId, String pieceKey, String label, String text) throws Exception {
        mockMvc.perform(multipart("/api/dossiers/{id}/elements/attachment", dossierId)
                        .file(new MockMultipartFile("file", pieceKey + ".pdf", "application/pdf", pdf(text)))
                        .param("label", label).param("pieceKey", pieceKey)
                        .with(agent()).with(csrf()))
                .andExpect(status().isCreated());
    }

    private void decide(String dossierId, String runId, JsonNode finding, String decision) throws Exception {
        mockMvc.perform(put("/api/dossiers/{id}/prechecks/{runId}/findings/{findingId}/decision", dossierId, runId,
                        finding.get("id").asString()).with(agent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"" + decision + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value(decision));
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
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
