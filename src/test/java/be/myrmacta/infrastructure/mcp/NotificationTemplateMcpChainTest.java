package be.myrmacta.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import be.myrmacta.domain.assistant.port.out.DesignToolsPort;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §123 (CS7) — les trois outils MCP des gabarits de notification par la vraie chaîne HTTP, sécurité M2M et
 * transport MCP : refus sans le rôle de service ou sans jeton, succès avec lui, brouillon seulement, aucun corps renvoyé. Le
 * chemin « assistant » (catalogue de conception, acteur de la session) est éprouvé sur le contexte réel : refus hors
 * {@code TENANT_ADMIN}, succès pour {@code TENANT_ADMIN}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class NotificationTemplateMcpChainTest {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ADMIN = AccessContext.user("alice", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User MANAGER = AccessContext.user("bob", Set.of("DOSSIER_MANAGER"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private NotificationTemplateRepository repository;
    @Autowired private DesignToolsPort catalog;

    private String sessionId;

    @BeforeEach
    void initializeSession() throws Exception {
        MvcResult init = callMcp(sessionId, "API_SERVICE", 1, "initialize", """
                {"protocolVersion": "2025-06-18", "capabilities": {},
                 "clientInfo": {"name": "myrmacta-test", "version": "0.1.0"}}
                """);
        sessionId = init.getResponse().getHeader("Mcp-Session-Id");
    }

    private static String newKey() {
        return "mcp-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<NotificationTemplate> versions(String key) {
        return repository.pageByTenantId(TENANT, key, PageQuery.of(0, 100)).content();
    }

    private String callTool(String tool, Map<String, Object> arguments) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", tool);
        params.put("arguments", arguments);
        MvcResult call = callMcp(sessionId, "API_SERVICE", 2, "tools/call", mapper.writeValueAsString(params));
        assertThat(call.getResponse().getStatus()).isLessThan(400);
        return call.getResponse().getContentAsString();
    }

    @Test
    void theThreeToolsAreRegisteredOnTheMcpEndpoint() throws Exception {
        MvcResult list = callMcp(sessionId, "API_SERVICE", 2, "tools/list", "{}");

        assertThat(list.getResponse().getContentAsString())
                .contains("\"name\":\"define_notification_template\"")
                .contains("\"name\":\"list_notification_templates\"")
                .contains("\"name\":\"preview_notification_template\"");
    }

    @Test
    void withTheServiceRole_defineWritesADraftOnly_listShowsIt_andPreviewRenders() throws Exception {
        String key = newKey();
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("templateKey", key);
        definition.put("locale", "fr");
        definition.put("label", "Accusé de réception");
        definition.put("subject", "Dossier [[${dossier.reference}]]");
        definition.put("textBody", "Bonjour [[${variables.prenom}]], votre dossier est enregistré. SECRET-TEXTE");
        definition.put("htmlBody", "<p>Bonjour <span th:text=\"${variables.prenom}\">x</span> SECRET-HTML</p>");

        String defined = callTool("define_notification_template", definition);

        assertThat(defined).contains("\"isError\":false").contains("create").contains("DRAFT");
        // jamais un corps dans la réponse
        assertThat(defined).doesNotContain("SECRET-TEXTE").doesNotContain("SECRET-HTML");
        assertThat(versions(key)).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(TemplateStatus.DRAFT);
            assertThat(t.locale()).isEqualTo("fr");
            assertThat(t.updatedBy()).isEqualTo("agent-1");
            assertThat(t.textBody()).contains("SECRET-TEXTE");
        });

        String listed = callTool("list_notification_templates", Map.of("templateKey", key));
        assertThat(listed).contains("\"isError\":false").contains(key).contains("DRAFT")
                .doesNotContain("SECRET-TEXTE").doesNotContain("SECRET-HTML");

        Map<String, Object> previewByKey = new LinkedHashMap<>();
        previewByKey.put("templateKey", key);
        previewByKey.put("locale", "fr");
        previewByKey.put("model", Map.of("dossier", Map.of("reference", "D-42"), "variables", Map.of("prenom", "Lea")));
        String previewed = callTool("preview_notification_template", previewByKey);
        assertThat(previewed).contains("\"isError\":false").contains("Dossier D-42").contains("Bonjour Lea");

        // une variable absente du modèle fait échouer l'aperçu, comme à l'envoi
        previewByKey.put("model", Map.of("dossier", Map.of("reference", "D-42")));
        assertThat(callTool("preview_notification_template", previewByKey)).contains("\"isError\":true");

        // redéfinir un brouillon le remplace : aucune version de plus
        definition.put("textBody", "Version 2");
        assertThat(callTool("define_notification_template", definition)).contains("\"isError\":false").contains("update");
        assertThat(versions(key)).singleElement().satisfies(t -> assertThat(t.textBody()).isEqualTo("Version 2"));
    }

    @Test
    void anActiveVersionIsNeverModified_andADryRunWritesNothing() throws Exception {
        String key = newKey();
        NotificationTemplate v1 = repository.save(new NotificationTemplate(UUID.randomUUID(), key, null, null, "L", 1,
                TemplateStatus.ACTIVE, "Objet", "Texte actif", null, TENANT, java.time.Instant.now(), java.time.Instant.now(), "alice"));

        String dry = callTool("define_notification_template", Map.of(
                "templateKey", key, "subject", "Nouvel objet", "textBody", "Nouveau texte", "dryRun", true));
        assertThat(dry).contains("\"isError\":false").contains("newVersion").contains("dryRun");
        assertThat(versions(key)).hasSize(1);

        String written = callTool("define_notification_template", Map.of(
                "templateKey", key, "subject", "Nouvel objet", "textBody", "Nouveau texte"));
        assertThat(written).contains("\"isError\":false").contains("newVersion");
        assertThat(versions(key)).hasSize(2);
        assertThat(repository.findByIdAndTenantId(v1.id(), TENANT).orElseThrow())
                .satisfies(t -> {
                    assertThat(t.status()).isEqualTo(TemplateStatus.ACTIVE);
                    assertThat(t.textBody()).isEqualTo("Texte actif");
                });
        assertThat(versions(key)).filteredOn(t -> t.version() == 2).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(TemplateStatus.DRAFT));
    }

    @Test
    void aTemplateOutsideTheHtmlPolicy_isRefusedAsAToolError_andNothingIsWritten() throws Exception {
        String key = newKey();

        String body = callTool("define_notification_template", Map.of(
                "templateKey", key,
                "subject", "Objet",
                "textBody", "Texte",
                "htmlBody", "<p><img src=\"https://example.com/pixel.png\"></p>"));

        assertThat(body).contains("\"isError\":true").contains("htmlBody");
        assertThat(versions(key)).isEmpty();
        String dry = callTool("define_notification_template", Map.of(
                "templateKey", key, "subject", "Objet", "textBody", "Texte", "dryRun", true,
                "htmlBody", "<p><img src=\"https://example.com/pixel.png\"></p>"));
        assertThat(dry).contains("\"isError\":false").contains("INVALID");
        assertThat(versions(key)).isEmpty();
    }

    @Test
    void withoutTheServiceRole_orWithoutAToken_everyToolIsRefusedByTheHttpChain() throws Exception {
        for (String tool : List.of("define_notification_template", "list_notification_templates", "preview_notification_template")) {
            String params = "{\"name\":\"%s\",\"arguments\":{\"templateKey\":\"x\"}}".formatted(tool);
            // un administrateur de tenant qui n'a pas le rôle de service M2M n'entre pas
            MvcResult asAdmin = mockMvc.perform(post("/api/m2m/mcp")
                            .with(jwt().jwt(b -> b.claim("tenant_id", TENANT))
                                    .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))
                            .header("Mcp-Session-Id", sessionId)
                            .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":" + params + "}"))
                    .andReturn();
            assertThat(asAdmin.getResponse().getStatus()).as(tool).isEqualTo(403);
            // le rôle de service d'un autre tenant est refusé aussi
            assertThat(callMcp(sessionId, "other_tenant", "API_SERVICE", 4, "tools/call", params).getResponse().getStatus())
                    .as(tool).isEqualTo(403);
            // sans jeton
            mockMvc.perform(post("/api/m2m/mcp")
                            .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":" + params + "}"))
                    .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        }
    }

    @Test
    void throughTheAssistantCatalog_aTenantAdminProposesAndConfirms_anyOtherRoleIsRefused() {
        String key = newKey();
        String arguments = """
                {"templateKey": "%s", "subject": "Objet", "textBody": "Texte de l'assistant"}
                """.formatted(key);

        assertThat(catalog.specs()).extracting(s -> s.name())
                .contains("define_notification_template", "list_notification_templates", "preview_notification_template");
        // l'assistant propose (validé à blanc), l'humain confirme : jamais d'écriture directe
        assertThat(catalog.needsConfirmation("define_notification_template")).isTrue();
        assertThatThrownBy(() -> catalog.invoke("define_notification_template", arguments, ADMIN))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class);
        assertThat(catalog.preview("define_notification_template", arguments, ADMIN)).contains("\"dryRun\":true");
        assertThat(versions(key)).isEmpty();

        assertThat(catalog.invokeConfirmed("define_notification_template", arguments, ADMIN)).contains("create");
        assertThat(versions(key)).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(TemplateStatus.DRAFT);
            assertThat(t.updatedBy()).isEqualTo("alice");
        });

        // le même appel par un acteur qui n'est pas TENANT_ADMIN est refusé, par chaque outil, sans rien écrire
        String otherKey = newKey();
        String otherArguments = arguments.replace(key, otherKey);
        assertThatThrownBy(() -> catalog.invokeConfirmed("define_notification_template", otherArguments, MANAGER))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class)
                .hasMessageContaining("TENANT_ADMIN");
        assertThatThrownBy(() -> catalog.invoke("preview_notification_template",
                        "{\"subject\": \"Objet\", \"textBody\": \"Texte\"}", MANAGER))
                .isInstanceOf(DesignToolsPort.ToolInvocationException.class)
                .hasMessageContaining("TENANT_ADMIN");
        assertThat(versions(otherKey)).isEmpty();
        assertThat(catalog.invoke("preview_notification_template",
                        "{\"subject\": \"Objet\", \"textBody\": \"Texte\"}", ADMIN))
                .contains("Texte");
    }

    private MvcResult callMcp(String session, String authority, int id, String method, String params) throws Exception {
        return callMcp(session, TENANT, authority, id, method, params);
    }

    private MvcResult callMcp(String session, String tenantId, String authority, int id, String method, String params)
            throws Exception {
        var request = post("/api/m2m/mcp")
                .with(m2mJwt(tenantId, authority))
                .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}".formatted(id, method, params));
        if (session != null) {
            request = request.header("Mcp-Session-Id", session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static RequestPostProcessor m2mJwt(String tenantId, String authority) {
        return jwt().jwt(builder -> builder
                        .claim("sub", "agent-1")
                        .claim("tenant_id", tenantId)
                        .claim("roles", List.of(authority)))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
