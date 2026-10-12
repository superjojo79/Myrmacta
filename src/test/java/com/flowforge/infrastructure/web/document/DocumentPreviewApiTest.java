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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

    /**
     * Les limiteurs comptent par minute calendaire : avec l'horloge système, un test de quota qui
     * chevauche une frontière de minute voit son compteur remis à zéro (200 au lieu de 429). Une
     * horloge figée garde toutes les requêtes d'un test dans la même fenêtre.
     */
    @TestConfiguration
    static class FixedClockRateLimitersConfig {

        private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);

        @Bean
        @Primary
        DocumentPreviewHtmlRateLimiter fixedClockPreviewHtmlRateLimiter() {
            return new DocumentPreviewHtmlRateLimiter(FIXED);
        }

        @Bean
        @Primary
        DocumentGenerationRateLimiter fixedClockGenerationRateLimiter() {
            return new DocumentGenerationRateLimiter(FIXED);
        }
    }

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
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
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
    void preview_publishedKey_allowedForWorkflowAndManagerWithViewType() throws Exception {
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

        grantViewTypeListingTemplate("dm-key", key);
        mockMvc.perform(post("/api/documents/preview")
                        .with(user("dm-key", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    /**
     * TODO §57 revue adversariale PR #113 : /preview et /preview/pdf laissaient un
     * DOSSIER_MANAGER prévisualiser (HTML ou PDF) n'importe quel templateKey du tenant, sans le
     * contrôle VIEW sur un type de dossier listant ce template déjà appliqué à /generate
     * (assertCanGenerate, TODO §57 M12). Ce test échouait avant correctif (200 au lieu de 403).
     */
    @Test
    void previewHtml_dossierManagerWithoutViewType_isForbidden() throws Exception {
        String key = createActiveTemplate();

        mockMvc.perform(post("/api/documents/preview")
                        .with(user("dm-no-acl", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": { "title": "X" } }
                                """.formatted(key)))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewPdf_dossierManagerWithoutViewType_isForbidden() throws Exception {
        String key = createActiveTemplate();

        mockMvc.perform(post("/api/documents/preview/pdf")
                        .with(user("dm-no-acl-pdf", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": { "title": "X" } }
                                """.formatted(key)))
                .andExpect(status().isForbidden());
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

    private void grantViewTypeListingTemplate(String subjectUserId, String templateKey) throws Exception {
        String code = "PREV" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("prev-type-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Label %s",
                                  "workflowKey": "workflow-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": [],
                                  "documentTemplateKeys": ["%s"]
                                }
                                """.formatted(code, code, code, templateKey)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("prev-type-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "%s",
                                  "permission": "VIEW"
                                }
                                """.formatted(subjectUserId)))
                .andExpect(status().isNoContent());
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
