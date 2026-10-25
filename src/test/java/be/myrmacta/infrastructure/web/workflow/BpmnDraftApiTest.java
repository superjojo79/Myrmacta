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

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class BpmnDraftApiTest {

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="http://flowforge/test">
              <process id="%s" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void get_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/workflow-bpmn-drafts/{key}", "draftDenied" + System.nanoTime())
                        .with(user("plain-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_existingDraft_returns200WithBody() throws Exception {
        String key = "draftGet" + System.nanoTime();
        putDraft(key, BPMN.formatted(key));

        mockMvc.perform(get("/api/workflow-bpmn-drafts/{key}", key).with(user("admin-draft-get", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processDefinitionKey").value(key))
                .andExpect(jsonPath("$.xml").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").value("admin-draft-get"));
    }

    @Test
    void get_noDraftForKey_returns204() throws Exception {
        mockMvc.perform(get("/api/workflow-bpmn-drafts/{key}", "draftMissing" + System.nanoTime())
                        .with(user("admin-draft-missing", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void put_validXmlWithMatchingKey_returns200AndUpsertsExistingDraft() throws Exception {
        String key = "draftPut" + System.nanoTime();

        putDraft(key, BPMN.formatted(key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processDefinitionKey").value(key));

        mockMvc.perform(put("/api/workflow-bpmn-drafts/{key}", key)
                        .with(user("admin-draft-put2", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": %s}".formatted(quoteJson(BPMN.formatted(key) + "<!-- v2 -->"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("admin-draft-put2"));

        mockMvc.perform(get("/api/workflow-bpmn-drafts/{key}", key).with(user("admin-draft-verify", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("admin-draft-put2"));
    }

    @Test
    void put_malformedXml_returns400() throws Exception {
        String key = "draftBad" + System.nanoTime();

        mockMvc.perform(put("/api/workflow-bpmn-drafts/{key}", key)
                        .with(user("admin-draft-bad", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": \"<not-even-xml\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/workflow-bpmn-drafts/{key}", "draftPutDenied" + System.nanoTime())
                        .with(user("plain-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": \"<definitions/>\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_missingXmlField_returns400() throws Exception {
        mockMvc.perform(put("/api/workflow-bpmn-drafts/{key}", "draftNoXml" + System.nanoTime())
                        .with(user("admin-draft-noxml", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_pathKeyDoesNotMatchXmlProcessId_returns400() throws Exception {
        String pathKey = "draftMismatchPath" + System.nanoTime();
        String xmlKey = "draftMismatchXml" + System.nanoTime();

        mockMvc.perform(put("/api/workflow-bpmn-drafts/{key}", pathKey)
                        .with(user("admin-draft-mismatch", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": %s}".formatted(quoteJson(BPMN.formatted(xmlKey)))))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions putDraft(String key, String xml) throws Exception {
        return mockMvc.perform(put("/api/workflow-bpmn-drafts/{key}", key)
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
