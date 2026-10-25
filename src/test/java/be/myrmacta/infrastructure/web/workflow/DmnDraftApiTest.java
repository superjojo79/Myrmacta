package be.myrmacta.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Miroir de {@code BpmnDraftApiTest} pour {@code /api/dmn-decision-drafts} (TODO §61 M5/M6 —
 * jamais couvert par un test API dédié avant la revue Phase 7, gap trouvé en relisant le diff
 * fichier par fichier).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DmnDraftApiTest {

    private static final String DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                         namespace="http://flowforge/test">
              <decision id="%s" name="Def">
                <decisionTable id="dt" hitPolicy="UNIQUE">
                  <input>
                    <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                  </input>
                  <output id="o" name="result" typeRef="string" />
                  <rule>
                    <inputEntry id="in"><text>"BRONZE"</text></inputEntry>
                    <outputEntry id="out"><text>'ok'</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void get_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/dmn-decision-drafts/{key}", "draftDenied" + System.nanoTime())
                        .with(user("plain-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_existingDraft_returns200WithBody() throws Exception {
        String key = "draftGet" + System.nanoTime();
        putDraft(key, DMN.formatted(key));

        mockMvc.perform(get("/api/dmn-decision-drafts/{key}", key).with(user("admin-draft-get", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionKey").value(key))
                .andExpect(jsonPath("$.xml").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").value("admin-draft-get"));
    }

    @Test
    void get_noDraftForKey_returns204() throws Exception {
        mockMvc.perform(get("/api/dmn-decision-drafts/{key}", "draftMissing" + System.nanoTime())
                        .with(user("admin-draft-missing", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void put_validXmlWithMatchingKey_returns200AndUpsertsExistingDraft() throws Exception {
        String key = "draftPut" + System.nanoTime();

        putDraft(key, DMN.formatted(key)).andExpect(status().isOk()).andExpect(jsonPath("$.decisionKey").value(key));

        mockMvc.perform(put("/api/dmn-decision-drafts/{key}", key)
                        .with(user("admin-draft-put2", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": %s}".formatted(quoteJson(DMN.formatted(key) + "<!-- v2 -->"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("admin-draft-put2"));

        mockMvc.perform(get("/api/dmn-decision-drafts/{key}", key).with(user("admin-draft-verify", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("admin-draft-put2"));
    }

    @Test
    void put_malformedXml_returns400() throws Exception {
        String key = "draftBad" + System.nanoTime();

        mockMvc.perform(put("/api/dmn-decision-drafts/{key}", key)
                        .with(user("admin-draft-bad", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": \"<not-even-xml\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/dmn-decision-drafts/{key}", "draftPutDenied" + System.nanoTime())
                        .with(user("plain-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": \"<definitions/>\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_missingXmlField_returns400() throws Exception {
        mockMvc.perform(put("/api/dmn-decision-drafts/{key}", "draftNoXml" + System.nanoTime())
                        .with(user("admin-draft-noxml", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_pathKeyDoesNotMatchXmlDecisionId_returns400() throws Exception {
        String pathKey = "draftMismatchPath" + System.nanoTime();
        String xmlKey = "draftMismatchXml" + System.nanoTime();

        mockMvc.perform(put("/api/dmn-decision-drafts/{key}", pathKey)
                        .with(user("admin-draft-mismatch", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": %s}".formatted(quoteJson(DMN.formatted(xmlKey)))))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions putDraft(String key, String xml) throws Exception {
        return mockMvc.perform(put("/api/dmn-decision-drafts/{key}", key)
                .with(user("admin-draft-get", "TENANT_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"xml\": %s}".formatted(quoteJson(xml))));
    }

    private static String quoteJson(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        String[] effective = roles.length == 0 ? new String[] {"DOSSIER_MANAGER"} : roles;
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", "tenant_dev"))
                .authorities(Arrays.stream(effective)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
