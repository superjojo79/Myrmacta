package com.flowforge.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §84 (LC4b, risque « HTML produit refusé par l'allowlist ») — le HTML que l'éditeur visuel écrit
 * ({@code src/test/resources/template-editor/editor-output.html}, aussi relu et réécrit à l'identique par le test Vitest de
 * l'éditeur) est accepté par l'allowlist §40 INCHANGÉE et rendu par le vrai moteur, aperçu HTML comme PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class TemplateEditorOutputRenderingTest {

    private static final String SAMPLE = """
            {"numero": "F-2026-001",
             "client": {"nom": "ACME", "pro": true, "type": "PRO"},
             "lignes": [{"designation": "Audit", "prix": "1200"}, {"designation": "Support", "prix": "300"}],
             "mentions": ["TVA non applicable", "Paiement à 30 jours"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor admin() {
        return oidcLogin()
                .idToken(t -> t.subject("tpl-admin").claim("tenant_id", "tenant_dev").claim("email", "a@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }

    private static String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/template-editor/editor-output.html"), StandardCharsets.UTF_8).strip();
    }

    private String body(String html, String variablesJson) throws Exception {
        var mapper = JsonMapper.builder().build();
        return mapper.writeValueAsString(java.util.Map.of("htmlContent", html, "variables", mapper.readValue(variablesJson, java.util.Map.class)));
    }

    @Test
    void theEditorsOutputIsAcceptedByTheAllowlistAndRenderedByTheRealEngine() throws Exception {
        String html = mockMvc.perform(post("/api/documents/preview").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(fixture(), SAMPLE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("F-2026-001").contains("Bonjour").contains("ACME");
        assertThat(html).contains("Client professionnel").doesNotContain("Client particulier"); // th:if / th:unless
        assertThat(html).contains("Tarif PRO"); // th:if + #strings.equals
        assertThat(html).contains("Audit").contains("Support").contains("1200").contains("300"); // th:each sur une ligne de tableau
        assertThat(html).contains("TVA non applicable").contains("Paiement à 30 jours"); // th:each sur un bloc
        assertThat(html).contains("page-break-after");
        assertThat(html).doesNotContain(" th:").doesNotContain("«"); // aucune construction non résolue ni repère visuel
    }

    @Test
    void theElseBranchesRenderWhenTheConditionsAreFalse() throws Exception {
        String variables = SAMPLE.replace("\"pro\": true", "\"pro\": false").replace("\"type\": \"PRO\"", "\"type\": \"PART\"");
        String html = mockMvc.perform(post("/api/documents/preview").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(fixture(), variables)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Client particulier").doesNotContain("Client professionnel").doesNotContain("Tarif PRO");
    }

    @Test
    void theEditorsOutputAlsoProducesAPdf() throws Exception {
        byte[] pdf = mockMvc.perform(post("/api/documents/preview/pdf").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(fixture(), SAMPLE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
