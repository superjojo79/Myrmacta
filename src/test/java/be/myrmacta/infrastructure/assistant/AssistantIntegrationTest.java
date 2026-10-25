package be.myrmacta.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.assistant.model.ChatMessage;
import be.myrmacta.domain.assistant.model.Conversation;
import be.myrmacta.domain.assistant.port.out.ConversationRepository;
import be.myrmacta.domain.assistant.port.out.DesignToolsPort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.port.in.ManageDraftsUseCase;
import be.myrmacta.infrastructure.mcp.McpToolModes;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §86 (LC3) — assistant de conception sur le contexte réel : API (désactivée par défaut), catalogue d'outils, persistance. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class AssistantIntegrationTest {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ALICE = AccessContext.user("alice", Set.of("TENANT_ADMIN"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DesignToolsPort catalog;

    @Autowired
    private ConversationRepository repository;

    @Autowired
    private ManageDraftsUseCase drafts;

    @Autowired
    private be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository schemas;

    private static RequestPostProcessor as(String subject, String role) {
        return oidcLogin()
                .idToken(t -> t.subject(subject).claim("tenant_id", TENANT).claim("email", subject + "@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    // ---- API ----

    @Test
    void theAssistantIsDisabledByDefault_noOutgoingCall_andStatusSaysSo() throws Exception {
        mockMvc.perform(get("/api/assistant/status").with(as("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(post("/api/assistant/messages").with(as("alice", "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"bonjour\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void onlyDesignersAccessTheAssistant() throws Exception {
        mockMvc.perform(get("/api/assistant/status").with(as("m", "DOSSIER_MANAGER"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/assistant/messages").with(as("m", "DOSSIER_MANAGER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assistant/status").with(as("w", "WORKFLOW_ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void conversationsArePrivateToTheirAuthor_andCanBeReadAndDeleted() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        repository.create(new Conversation(id, TENANT, "alice-api", "Mon sinistre", now, now, 1, 10, 5));
        repository.saveMessage(new ChatMessage(UUID.randomUUID(), id, 0, ChatMessage.Role.USER, "bonjour", null, now));
        repository.saveMessage(new ChatMessage(UUID.randomUUID(), id, 1, ChatMessage.Role.ASSISTANT, "salut", null, now));

        mockMvc.perform(get("/api/assistant/conversations").with(as("alice-api", "TENANT_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Mon sinistre"));
        mockMvc.perform(get("/api/assistant/conversations/" + id).with(as("alice-api", "TENANT_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].content").value("salut"));
        // un autre administrateur ne la voit ni ne la supprime
        mockMvc.perform(get("/api/assistant/conversations/" + id).with(as("bob-api", "TENANT_ADMIN"))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/assistant/conversations/" + id).with(as("bob-api", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/assistant/conversations").with(as("bob-api", "TENANT_ADMIN")))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/assistant/conversations/" + id).with(as("alice-api", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(repository.messages(id)).isEmpty();
    }

    // ---- catalogue d'outils ----

    @Test
    void theCatalogOffersExactlyTheDesignToolsMinusThoseTheAssistantMayNotCall() {
        Set<String> expected = McpToolModes.toolsFor(McpToolModes.Mode.DESIGN).stream()
                .filter(n -> !McpDesignToolCatalog.DENIED.contains(n))
                .collect(Collectors.toSet());
        Set<String> offered = catalog.specs().stream().map(s -> s.name()).collect(Collectors.toSet());
        assertThat(offered).isEqualTo(expected);
        // jamais un outil d'opération (données de dossiers), de publication ni d'application de bundle
        for (String name : offered) {
            assertThat(McpToolModes.modeOf(name)).as(name).isNotEqualTo(McpToolModes.Mode.OPERATION);
        }
        assertThat(offered).doesNotContain("publish_draft", "import_configuration", "get_dossier", "search_dossiers", "start_workflow");
    }

    @Test
    void toolSchemasComeFromTheAnnotations() {
        var spec = catalog.specs().stream().filter(s -> s.name().equals("define_task_question_schema")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var props = (java.util.Map<String, Object>) spec.inputSchema().get("properties");
        assertThat(props).containsKeys("schemaKey", "label", "fields", "dryRun");
        assertThat(spec.inputSchema().get("required").toString()).contains("schemaKey");
        assertThat(spec.description()).isNotBlank();
    }

    @Test
    void anUnknownOrDeniedToolIsRefusedWithTheAvailableList() {
        assertThatThrownBy(() -> catalog.invoke("publish_draft", "{}", ALICE))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class).hasMessageContaining("unknown tool");
        assertThatThrownBy(() -> catalog.invoke("get_dossier", "{}", ALICE))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class);
        assertThatThrownBy(() -> catalog.invoke("list_connector_definitions", "not json", ALICE))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class);
    }

    @Test
    void theAssistantNeverWritesASecretIntoAConnector() {
        // y compris les champs que le schéma d'un connecteur déclare secrets sans que leur nom le laisse voir (revue LC3, M1)
        for (String key : new String[] {"webhookSecret", "password", "apiKey", "access_token", "Authorization", "client_secret",
                "authHeaderValue", "AUTHHEADERVALUE", "oauthPrivateKey", "sftpPrivateKey"}) {
            assertThatThrownBy(() -> catalog.preview("define_connector_instance",
                    "{\"name\":\"X\",\"connectorType\":\"http-rest\",\"config\":{\"" + key + "\":\"v\"}}", ALICE))
                    .as(key).isInstanceOf(DesignToolsPort.ToolInvocationException.class).hasMessageContaining("never writes secrets");
        }
        // clé imbriquée
        assertThatThrownBy(() -> catalog.preview("define_connector_instance",
                "{\"name\":\"X\",\"connectorType\":\"http-rest\",\"config\":{\"headers\":{\"x\":{\"clientSecret\":\"v\"}}}}", ALICE))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class).hasMessageContaining("never writes secrets");
    }

    @Test
    void toolsRunAsTheSessionUser_andTheirDraftsCarryTheAssistantProvenance() {
        String key = "assistant-" + UUID.randomUUID().toString().substring(0, 8);
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://myrmacta/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/><userTask id="wait" name="Wait"/><endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="wait"/><sequenceFlow id="f2" sourceRef="wait" targetRef="end"/>
                  </process>
                </definitions>""".formatted(key);
        var args = new tools.jackson.databind.json.JsonMapper().writeValueAsString(java.util.Map.of(
                "processDefinitionKey", key, "xml", xml, "label", "Assistant flow",
                "statusDefinitions", java.util.List.of(java.util.Map.of("code", "STARTED", "label", "Started", "initial", true, "outcome", "NONE")),
                "allowedDossierStatuses", java.util.List.of("OUVERT")));

        String result = catalog.invoke("deploy_workflow", args, ALICE);

        assertThat(result).contains(key);
        var draft = drafts.listPending(ALICE).stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
        assertThat(draft.provenance()).isEqualTo(Provenance.ASSISTANT);
    }

    @Test
    void anActorWithoutTheRightRoleCannotUseTheToolsEvenThroughTheAssistant() {
        AccessContext.User manager = AccessContext.user("m", Set.of("DOSSIER_MANAGER"));
        assertThatThrownBy(() -> catalog.preview("define_task_question_schema",
                "{\"schemaKey\":\"s\",\"label\":\"S\",\"fields\":[]}", manager))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class);
    }

    // ---- écritures proposées (revue LC3, M2) ----

    @Test
    void directWritesAreNeverExecutedByInvoke_theyAreValidatedAtDryRunAndWrittenOnlyWhenConfirmed() {
        String key = "proposed-" + UUID.randomUUID().toString().substring(0, 8);
        String args = "{\"schemaKey\":\"" + key + "\",\"label\":\"Proposé\",\"fields\":[{\"name\":\"nom\",\"label\":\"Nom\",\"type\":\"STRING\",\"required\":false}]}";

        assertThat(catalog.needsConfirmation("define_task_question_schema")).isTrue();
        assertThatThrownBy(() -> catalog.invoke("define_task_question_schema", args, ALICE))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class).hasMessageContaining("must be proposed");

        String validation = catalog.preview("define_task_question_schema", args, ALICE);
        assertThat(validation).contains("\"dryRun\":true");
        assertThat(schemas.findByKey(TENANT, key)).as("l'aperçu n'écrit rien").isEmpty();

        // un dryRun:false glissé par le modèle est forcé à blanc à l'aperçu
        catalog.preview("define_task_question_schema", args.replace("}]}", "}],\"dryRun\":false}"), ALICE);
        assertThat(schemas.findByKey(TENANT, key)).isEmpty();

        catalog.invokeConfirmed("define_task_question_schema", args, ALICE);
        assertThat(schemas.findByKey(TENANT, key)).isPresent();
        assertThatThrownBy(() -> catalog.invokeConfirmed("deploy_workflow", "{}", ALICE)).isInstanceOf(DesignToolsPort.ToolInvocationException.class);
    }

    @Test
    void theHumanAppliesAProposalOnceThroughTheApi_withHisOwnIdentity() throws Exception {
        String key = "api-applied-" + UUID.randomUUID().toString().substring(0, 8);
        UUID conversation = UUID.randomUUID();
        UUID action = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        repository.create(new Conversation(conversation, TENANT, "carol", "Proposition", now, now, 0, 0, 0));
        repository.saveAction(new be.myrmacta.domain.assistant.model.AssistantAction(action, conversation, "define_task_question_schema",
                "{\"schemaKey\":\"" + key + "\",\"label\":\"L\",\"fields\":[{\"name\":\"a\",\"label\":\"A\",\"type\":\"STRING\",\"required\":false}]}",
                be.myrmacta.domain.assistant.model.AssistantAction.Status.PENDING, null, now, null));

        mockMvc.perform(get("/api/assistant/conversations/" + conversation + "/actions").with(as("carol", "TENANT_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PENDING"));
        // un autre administrateur ne voit ni n'applique la proposition d'un autre
        mockMvc.perform(post("/api/assistant/conversations/" + conversation + "/actions/" + action + "/apply")
                        .with(as("dave", "TENANT_ADMIN")).with(csrf())).andExpect(status().isNotFound());
        assertThat(schemas.findByKey(TENANT, key)).isEmpty();

        mockMvc.perform(post("/api/assistant/conversations/" + conversation + "/actions/" + action + "/apply")
                        .with(as("carol", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));
        assertThat(schemas.findByKey(TENANT, key)).isPresent();
        // une seule application
        mockMvc.perform(post("/api/assistant/conversations/" + conversation + "/actions/" + action + "/apply")
                        .with(as("carol", "TENANT_ADMIN")).with(csrf())).andExpect(status().isConflict());
        mockMvc.perform(post("/api/assistant/conversations/" + conversation + "/actions/" + action + "/dismiss")
                        .with(as("carol", "TENANT_ADMIN")).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void aFailingProposalIsKeptAsFailedWithItsReason_andADismissedOneIsNeverApplied() throws Exception {
        UUID conversation = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        UUID dismissed = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        repository.create(new Conversation(conversation, TENANT, "erin", "Echec", now, now, 0, 0, 0));
        repository.saveAction(new be.myrmacta.domain.assistant.model.AssistantAction(bad, conversation, "define_task_question_schema",
                "{\"schemaKey\":\"x\"}", be.myrmacta.domain.assistant.model.AssistantAction.Status.PENDING, null, now, null));
        repository.saveAction(new be.myrmacta.domain.assistant.model.AssistantAction(dismissed, conversation, "define_task_question_schema",
                "{}", be.myrmacta.domain.assistant.model.AssistantAction.Status.PENDING, null, now, null));

        mockMvc.perform(post("/api/assistant/conversations/" + conversation + "/actions/" + bad + "/apply")
                        .with(as("erin", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.outcome").value(org.hamcrest.Matchers.containsString("missing required argument")));
        mockMvc.perform(post("/api/assistant/conversations/" + conversation + "/actions/" + dismissed + "/dismiss")
                        .with(as("erin", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISMISSED"));
        mockMvc.perform(post("/api/assistant/conversations/" + conversation + "/actions/" + dismissed + "/apply")
                        .with(as("erin", "TENANT_ADMIN")).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void aConversationBeingProcessedCannotBeDeletedNorUpdatedIntoExistenceAgain() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now(); // le contrôleur lit l'horloge réelle
        repository.create(new Conversation(id, TENANT, "frank", "Occupée", now, now, 0, 0, 0));
        assertThat(repository.tryLock(id, now, now.plusSeconds(600))).isTrue();
        assertThat(repository.tryLock(id, now.plusSeconds(1), now.plusSeconds(601))).as("un seul tour à la fois").isFalse();
        mockMvc.perform(delete("/api/assistant/conversations/" + id).with(as("frank", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
        // bail expiré : repris
        assertThat(repository.tryLock(id, now.plusSeconds(700), now.plusSeconds(1300))).isTrue();
        repository.unlock(id);
        mockMvc.perform(delete("/api/assistant/conversations/" + id).with(as("frank", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        // mise à jour après suppression : sans effet, la conversation ne ressuscite pas
        assertThat(repository.update(new Conversation(id, TENANT, "frank", "Occupée", now, now, 1, 1, 1))).isFalse();
        assertThat(repository.findByIdAndOwner(id, TENANT, "frank")).isEmpty();
    }

    // ---- persistance ----

    @Test
    void usageIsCountedPerDayAndSurvivesDeletingTheConversation() {
        String tenant = "tenant_usage_test";
        LocalDate day = LocalDate.of(2026, 10, 1);
        repository.addTokens(tenant, day, 100);
        repository.addTokens(tenant, day, 50);
        repository.addTokens(tenant, day.plusDays(1), 7);
        assertThat(repository.tokensOn(tenant, day)).isEqualTo(150);
        assertThat(repository.tokensOn(tenant, day.plusDays(1))).isEqualTo(7);
        assertThat(repository.tokensOn(tenant, day.minusDays(1))).isZero();
    }

    @Test
    void retentionPurgesOldConversationsWithTheirMessages() {
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        Instant oldDate = Instant.parse("2026-01-01T00:00:00Z");
        Instant newDate = Instant.parse("2026-09-30T00:00:00Z");
        repository.create(new Conversation(old, "tenant_purge", "u", "vieille", oldDate, oldDate, 1, 0, 0));
        repository.saveMessage(new ChatMessage(UUID.randomUUID(), old, 0, ChatMessage.Role.USER, "x", null, oldDate));
        repository.create(new Conversation(recent, "tenant_purge", "u", "récente", newDate, newDate, 1, 0, 0));

        int removed = repository.deleteOlderThan("tenant_purge", Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(removed).isEqualTo(1);
        assertThat(repository.findByIdAndOwner(old, "tenant_purge", "u")).isEmpty();
        assertThat(repository.messages(old)).isEmpty();
        assertThat(repository.findByIdAndOwner(recent, "tenant_purge", "u")).isPresent();
    }
}
