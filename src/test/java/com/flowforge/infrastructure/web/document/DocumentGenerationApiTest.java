package com.flowforge.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DocumentGenerationApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentStoragePort documentStoragePort;

    @Autowired
    private DocumentTemplateRepository documentTemplateRepository;

    @Value("${app.storage.filesystem.root}")
    private String storageRoot;

    @Test
    void generate_spelTypeExpression_returns400() throws Exception {
        String key = "ssti-gen-" + UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T12:00:00Z");
        documentTemplateRepository.save(new DocumentTemplate(
                UUID.randomUUID(),
                key,
                null,
                null,
                "ssti",
                1,
                TemplateStatus.ACTIVE,
                "<html><body><span th:text=\"${T(java.lang.Runtime).getRuntime()}\">x</span></body></html>",
                TENANT_ID,
                now,
                now,
                "admin"));

        mockMvc.perform(post("/api/documents/generate")
                        .with(user("gen-ssti", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": {} }
                                """.formatted(key)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_returnsPdf_withoutStoring() throws Exception {
        String key = createActiveTemplate();
        long before = countStoredFiles();

        MvcResult result = mockMvc.perform(post("/api/documents/generate")
                        .with(user("gen-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "%s",
                                  "variables": { "title": "Hello" }
                                }
                                """.formatted(key)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes());
        assertThat(countStoredFiles()).isEqualTo(before);
    }

    @Test
    void generate_roles_allowedAndDenied() throws Exception {
        String key = createActiveTemplate();
        String body = """
                { "templateKey": "%s", "variables": { "title": "X" } }
                """.formatted(key);

        mockMvc.perform(post("/api/documents/generate")
                        .with(user("wf", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/generate")
                        .with(user("mgr", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/documents/generate")
                        .with(user("viewer", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/documents/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generate_dossierManager_withViewTypeListingTemplate_succeeds() throws Exception {
        String key = createActiveTemplate();
        String code = "GEN" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("gen-type-admin", "TENANT_ADMIN"))
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
                                """.formatted(code, code, code, key)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("gen-type-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "mgr-allowed",
                                  "permission": "VIEW"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/documents/generate")
                        .with(user("mgr-allowed", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "%s", "variables": { "title": "X" } }
                                """.formatted(key)))
                .andExpect(status().isOk());
    }

    @Test
    void generate_unknownTemplate_returns404() throws Exception {
        mockMvc.perform(post("/api/documents/generate")
                        .with(user("gen-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "missing-%s", "variables": {} }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void generate_blankTemplateKey_returns400() throws Exception {
        mockMvc.perform(post("/api/documents/generate")
                        .with(user("gen-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "  ", "variables": {} }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_exceedingRateLimit_returns429() throws Exception {
        String key = createActiveTemplate();
        String caller = "rate-limited-" + UUID.randomUUID();
        String body = """
                { "templateKey": "%s", "variables": { "title": "X" } }
                """.formatted(key);
        int limitPerMinute = 30;

        for (int i = 0; i < limitPerMinute; i++) {
            mockMvc.perform(post("/api/documents/generate")
                            .with(user(caller, "TENANT_ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/documents/generate")
                        .with(user(caller, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void generate_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/documents/generate")
                        .with(user("gen-admin", "TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "x", "variables": {} }
                                """))
                .andExpect(status().isForbidden());
    }

    private String createActiveTemplate() throws Exception {
        String key = "gen-" + UUID.randomUUID();
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
