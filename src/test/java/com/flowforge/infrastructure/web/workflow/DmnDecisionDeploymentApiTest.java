package com.flowforge.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Miroir de {@code WorkflowBpmnDeploymentApiTest} pour {@code /api/dmn-decision-deployments}
 * (TODO §61 M6 — jamais couvert par un test API dédié avant la revue Phase 7). Contrairement au
 * déploiement BPMN, une table de décision DMN n'a pas de garde-fou "clé déjà connue" équivalent à
 * {@code WorkflowTypeConfigRepository} : aucune configuration préalable n'est requise avant de
 * déployer (voir {@code DeployDecisionTableUseCaseImpl}, sans check de type analogue).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DmnDecisionDeploymentApiTest {

    private static final String TENANT_ID = "tenant_dev";

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
    void deploy_withoutAdminRole_returns403() throws Exception {
        MockMultipartFile file = dmnFile("apiDenied" + System.nanoTime());

        mockMvc.perform(multipart("/api/dmn-decision-deployments").file(file).with(user("plain-user")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deploy_validDmn_returns201WithDeploymentResult() throws Exception {
        String key = "apiKnown" + System.nanoTime();
        MockMultipartFile file = dmnFile(key);

        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(file)
                        .with(user("admin-deploy", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisionKey").value(key))
                .andExpect(jsonPath("$.deployedVersion").value(1))
                .andExpect(jsonPath("$.deploymentId").isNotEmpty());
    }

    @Test
    void deploy_invalidDmn_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.dmn", "application/xml", "<not-even-xml".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(file)
                        .with(user("admin-invalid", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deploy_disallowedContentType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "table.dmn", "application/x-sh", "echo hi".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(file)
                        .with(user("admin-mime", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deploy_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.dmn", "application/xml", new byte[0]);

        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(file)
                        .with(user("admin-empty", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void info_deployedKey_returns200WithInfo() throws Exception {
        String key = "apiInfo" + System.nanoTime();
        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(dmnFile(key))
                        .with(user("admin-info", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dmn-decision-deployments/info")
                        .param("decisionKey", key)
                        .with(user("admin-info-get", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionKey").value(key))
                .andExpect(jsonPath("$.latestVersion").value(1));
    }

    @Test
    void info_neverDeployedKey_returns204() throws Exception {
        mockMvc.perform(get("/api/dmn-decision-deployments/info")
                        .param("decisionKey", "apiNeverDeployed" + System.nanoTime())
                        .with(user("admin-info-empty", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deployedXml_deployedKey_returns200WithXmlBody() throws Exception {
        String key = "apiXml" + System.nanoTime();
        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(dmnFile(key))
                        .with(user("admin-xml", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dmn-decision-deployments/{key}/xml", key)
                        .with(user("admin-xml-get", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"" + key + "\"")));
    }

    @Test
    void deployedXml_neverDeployedKey_returns204() throws Exception {
        mockMvc.perform(get("/api/dmn-decision-deployments/{key}/xml", "apiXmlNeverDeployed" + System.nanoTime())
                        .with(user("admin-xml-empty", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deployedXml_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/dmn-decision-deployments/{key}/xml", "apiXmlDenied" + System.nanoTime())
                        .with(user("plain-user-xml")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deploy_successfulOnKeyWithExistingDraft_deletesTheDraft() throws Exception {
        String key = "apiDeployDeletesDraft" + System.nanoTime();
        mockMvc.perform(put("/api/dmn-decision-drafts/{key}", key)
                        .with(user("admin-draft-before-deploy", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": \"" + DMN.formatted(key).replace("\n", "\\n").replace("\"", "\\\"")
                                + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(dmnFile(key))
                        .with(user("admin-deploy-deletes-draft", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dmn-decision-drafts/{key}", key)
                        .with(user("admin-draft-after-deploy", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    private static MockMultipartFile dmnFile(String key) {
        return new MockMultipartFile(
                "file", "table.dmn", "application/xml", DMN.formatted(key).getBytes(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        String[] effective = roles.length == 0 ? new String[] {"DOSSIER_MANAGER"} : roles;
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID))
                .authorities(Arrays.stream(effective)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
