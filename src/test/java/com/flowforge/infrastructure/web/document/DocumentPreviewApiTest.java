package com.flowforge.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
@TestPropertySource(
        properties = {
            "app.document.preview.rate-limit-per-minute=2",
            "app.document.generate.rate-limit-per-minute=2"
        })
class DocumentPreviewApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.storage.filesystem.root}")
    private String storageRoot;

    @Test
    void previewHtml_live_returnsWrappedHtml_withoutStoring() throws Exception {
        long before = countStoredFiles();

        MvcResult result = mockMvc.perform(post("/api/documents/preview")
                        .with(user("preview-ta", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "htmlContent": "<p th:text=\\"${title}\\">x</p>",
                                  "variables": { "title": "Hello" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Hello");
        assertThat(body).contains("width: 210mm");
        assertThat(countStoredFiles()).isEqualTo(before);
    }

    @Test
    void previewHtml_templateKey_returnsActiveTemplate() throws Exception {
        String key = createActiveTemplate();

        MvcResult result = mockMvc.perform(post("/api/documents/preview")
                        .with(user("preview-key-ta", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": { "title": "Hello" } }
                                """.formatted(key)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Hello");
    }

    @Test
    void previewPdf_returnsInlinePdf_withoutStoring() throws Exception {
        long before = countStoredFiles();

        MvcResult result = mockMvc.perform(post("/api/documents/preview/pdf")
                        .with(user("preview-pdf-ta", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "htmlContent": "<p th:text=\\"${title}\\">x</p>",
                                  "variables": { "title": "Hello" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes());
        assertThat(countStoredFiles()).isEqualTo(before);
    }

    @Test
    void previewHtml_unknownTemplate_returns404() throws Exception {
        mockMvc.perform(post("/api/documents/preview")
                        .with(user("preview-404", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "missing-%s", "variables": {} }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewHtml_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/documents/preview")
                        .with(user("preview-400", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "htmlContent": "  ", "templateKey": "  " }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewHtml_viewerForbidden_anonymousUnauthorized() throws Exception {
        mockMvc.perform(post("/api/documents/preview")
                        .with(user("viewer", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "htmlContent": "<p>x</p>" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/documents/preview")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "htmlContent": "<p>x</p>" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preview_liveGate_workflowAndManagerForbidden() throws Exception {
        long before = countStoredFiles();
        String live = """
                { "htmlContent": "<p>secret</p>" }
                """;

        mockMvc.perform(post("/api/documents/preview")
                        .with(user("wa-live", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(live))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/documents/preview")
                        .with(user("dm-live", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(live))
                .andExpect(status().isForbidden());

        assertThat(countStoredFiles()).isEqualTo(before);
    }

    @Test
    void preview_liveWins_managerWithKeyStillForbidden() throws Exception {
        String key = createActiveTemplate();
        mockMvc.perform(post("/api/documents/preview")
                        .with(user("dm-both", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "htmlContent": "<p>live</p>", "templateKey": "%s" }
                                """.formatted(key)))
                .andExpect(status().isForbidden());
    }

    @Test
    void preview_publishedKey_allowedForWorkflowAndManager() throws Exception {
        String key = createActiveTemplate();
        String body = """
                { "templateKey": "%s", "variables": { "title": "X" } }
                """.formatted(key);

        mockMvc.perform(post("/api/documents/preview")
                        .with(user("wa-key", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/preview")
                        .with(user("dm-key", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void previewHtml_brokenThymeleaf_returns400() throws Exception {
        mockMvc.perform(post("/api/documents/preview")
                        .with(user("preview-thymeleaf", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "htmlContent": "<p th:text=\\"${unclosed\\">x</p>" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewHtml_spelTypeExpression_returns400() throws Exception {
        mockMvc.perform(post("/api/documents/preview")
                        .with(user("preview-ssti", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "htmlContent": "<p th:text=\\"${T(java.lang.Runtime).getRuntime()}\\">x</p>"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewHtml_rateLimitIsDedicated_generateDoesNotConsumeIt() throws Exception {
        String caller = "html-quota-" + UUID.randomUUID();
        String live = """
                { "htmlContent": "<p>x</p>" }
                """;
        String key = createActiveTemplate();

        mockMvc.perform(post("/api/documents/preview")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(live))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/preview")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(live))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/generate")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": { "title": "X" } }
                                """.formatted(key)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/preview")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(live))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void previewPdf_sharesGenerateRateLimit() throws Exception {
        String caller = "pdf-quota-" + UUID.randomUUID();
        String live = """
                { "htmlContent": "<p>x</p>" }
                """;
        String key = createActiveTemplate();

        mockMvc.perform(post("/api/documents/preview/pdf")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(live))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/generate")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": { "title": "X" } }
                                """.formatted(key)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/preview/pdf")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(live))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void generate_contentDispositionIsNotInline() throws Exception {
        String key = createActiveTemplate();
        MvcResult result = mockMvc.perform(post("/api/documents/generate")
                        .with(user("gen-disp", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": { "title": "X" } }
                                """.formatted(key)))
                .andExpect(status().isOk())
                .andReturn();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition == null || !disposition.contains("inline")).isTrue();
    }

    private String createActiveTemplate() throws Exception {
        String key = "prev-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/document-templates")
                        .with(user("tpl-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "%s",
                                  "label": "label",
                                  "htmlContent": "<html><body><span th:text=\\"${title}\\">x</span></body></html>"
                                }
                                """.formatted(key)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(created.getResponse().getContentAsString())
                .get("id")
                .asText();
        mockMvc.perform(post("/api/document-templates/" + id + "/activate")
                        .with(user("tpl-admin", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return key;
    }

    private long countStoredFiles() throws Exception {
        Path root = Path.of(storageRoot);
        if (!Files.exists(root)) {
            return 0;
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
