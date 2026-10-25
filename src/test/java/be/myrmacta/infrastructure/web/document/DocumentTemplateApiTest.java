package be.myrmacta.infrastructure.web.document;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DocumentTemplateApiTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createGetListActivateResolve_happyPath() throws Exception {
        String key = "api-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/document-templates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(key, "premium", "fr")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        String id = JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/document-templates/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("th:text")));

        mockMvc.perform(get("/api/document-templates")
                        .param("templateKey", key)
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].htmlContent").value(""))
                .andExpect(jsonPath("$.content[0].templateKey").value(key))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/document-templates")
                        .param("templateKey", key)
                        .param("includeContent", "true")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].htmlContent").value(org.hamcrest.Matchers.containsString("th:text")));

        mockMvc.perform(post("/api/document-templates/" + id + "/activate")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/document-templates/resolve")
                        .param("templateKey", key)
                        .param("variantKey", "premium")
                        .param("locale", "fr")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void patchActive_returns409_and_invalidActivate_returns400() throws Exception {
        String key = "patch-" + UUID.randomUUID();
        String id = createDraft(key);

        mockMvc.perform(post("/api/document-templates/" + id + "/activate")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/document-templates/" + id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"x","htmlContent":"<p>y</p>"}
                                """))
                .andExpect(status().isConflict());

        String badId = createDraftRaw(key + "-bad", "<th:block th:each=\"x : ${y}\"");
        mockMvc.perform(post("/api/document-templates/" + badId + "/activate")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDraft_spelTypeExpression_returns201() throws Exception {
        createDraftRaw("ssti-draft-" + UUID.randomUUID(), "<p th:text=\"${T(java.lang.Runtime).getRuntime()}\">x</p>");
    }

    @Test
    void activate_spelTypeExpression_returns400() throws Exception {
        String id = createDraftRaw(
                "ssti-" + UUID.randomUUID(),
                "<p th:text=\"${T(java.lang.Runtime).getRuntime()}\">x</p>");
        mockMvc.perform(post("/api/document-templates/" + id + "/activate")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archiveAndNewVersion_andSecurity() throws Exception {
        String key = "life-" + UUID.randomUUID();
        String id = createDraft(key);
        mockMvc.perform(post("/api/document-templates/" + id + "/activate")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/document-templates/" + id + "/new-version")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/document-templates/" + id + "/archive")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/document-templates").with(user("viewer", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/document-templates")
                        .param("size", "101")
                        .with(admin()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/document-templates")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/document-templates")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(key + "-csrf", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolve_missing_returns404() throws Exception {
        mockMvc.perform(get("/api/document-templates/resolve")
                        .param("templateKey", "missing-" + UUID.randomUUID())
                        .with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchDraft_createWithoutThymeleafParse_lifecycleConflicts_andResolveFallback() throws Exception {
        String key = "gap-" + UUID.randomUUID();

        // Create accepts invalid Thymeleaf (parse only on activate)
        String draftId = createDraftRaw(key, "<th:block th:each=\"x : ${y}\"");
        mockMvc.perform(patch("/api/document-templates/" + draftId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"patched","htmlContent":"<p th:text=\\"${title}\\">ok</p>"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("patched"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/document-templates/" + draftId + "/activate")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        // Activate already-ACTIVE → 409
        mockMvc.perform(post("/api/document-templates/" + draftId + "/activate")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isConflict());

        // Default ACTIVE (null/null) resolves when exact variant/locale missing
        mockMvc.perform(get("/api/document-templates/resolve")
                        .param("templateKey", key)
                        .param("variantKey", "missing-variant")
                        .param("locale", "de")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/document-templates/" + draftId + "/archive")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        // Archive non-ACTIVE → 409
        mockMvc.perform(post("/api/document-templates/" + draftId + "/archive")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/document-templates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateKey":" ","label":"x","htmlContent":"<p>y</p>"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/document-templates/" + UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    private String createDraft(String key) throws Exception {
        return createDraftRaw(key, "<p th:text=\"${title}\">x</p>");
    }

    private String createDraftRaw(String key, String html) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/document-templates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "%s",
                                  "label": "label",
                                  "htmlContent": %s
                                }
                                """.formatted(key, JSON.writeValueAsString(html))))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private static String createPayload(String key, String variant, String locale) {
        return """
                {
                  "templateKey": "%s",
                  "variantKey": %s,
                  "locale": %s,
                  "label": "label",
                  "htmlContent": "<p th:text=\\"${title}\\">x</p>"
                }
                """
                .formatted(
                        key,
                        variant == null ? "null" : "\"" + variant + "\"",
                        locale == null ? "null" : "\"" + locale + "\"");
    }

    private static RequestPostProcessor admin() {
        return user("template-admin", "TENANT_ADMIN");
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
