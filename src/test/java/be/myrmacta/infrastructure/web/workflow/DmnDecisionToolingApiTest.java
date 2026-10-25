package be.myrmacta.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DmnDecisionToolingApiTest {

    private static final String TENANT_ID = "tenant_dev";

    private static final String DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                         namespace="http://myrmacta/test">
              <decision id="%s" name="Def">
                <decisionTable id="dt" hitPolicy="UNIQUE">
                  <input>
                    <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                  </input>
                  <output id="o" name="result" typeRef="string" />
                  <rule>
                    <inputEntry id="in"><text>"BRONZE"</text></inputEntry>
                    <outputEntry id="out"><text>'%s'</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void evaluate_deployedTable_returnsRows() throws Exception {
        String key = "apiEval" + System.nanoTime();
        deploy(key, "ok");

        mockMvc.perform(post("/api/dmn-decision-deployments/evaluate")
                        .param("decisionKey", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{\"customerCat\":\"BRONZE\"}}")
                        .with(user("admin-eval", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.rows[0].result").value("ok"));
    }

    @Test
    void evaluate_unknownKey_returns404() throws Exception {
        mockMvc.perform(post("/api/dmn-decision-deployments/evaluate")
                        .param("decisionKey", "missing" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{}}")
                        .with(user("admin-miss", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void evaluate_noHit_returns200WithEmptyRows() throws Exception {
        String key = "apiMiss" + System.nanoTime();
        deploy(key, "ok");

        mockMvc.perform(post("/api/dmn-decision-deployments/evaluate")
                        .param("decisionKey", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{\"customerCat\":\"GOLD\"}}")
                        .with(user("admin-nohit", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isEmpty());
    }

    @Test
    void evaluate_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/dmn-decision-deployments/evaluate")
                        .param("decisionKey", "any")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{}}")
                        .with(user("manager"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testJsonAndCsv_reportPassAndFail() throws Exception {
        String key = "apiTest" + System.nanoTime();
        deploy(key, "ok");

        mockMvc.perform(post("/api/dmn-decision-deployments/test")
                        .param("decisionKey", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cases":[
                                  {"inputs":{"customerCat":"BRONZE"},"expectedRows":[{"result":"ok"}]},
                                  {"inputs":{"customerCat":"BRONZE"},"expectedRows":[{"result":"ko"}]}
                                ]}
                                """)
                        .with(user("admin-test", "WORKFLOW_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].passed").value(true))
                .andExpect(jsonPath("$.results[1].passed").value(false));

        mockMvc.perform(post("/api/dmn-decision-deployments/test")
                        .param("decisionKey", key)
                        .contentType("text/csv")
                        .content("input.customerCat,expected.result\nBRONZE,ok\n")
                        .with(user("admin-csv", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].passed").value(true));
    }

    @Test
    void test_emptyCasesAndTooManyCases_return400() throws Exception {
        String key = "apiCap" + System.nanoTime();
        deploy(key, "ok");

        mockMvc.perform(post("/api/dmn-decision-deployments/test")
                        .param("decisionKey", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cases\":[]}")
                        .with(user("admin-empty", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        StringBuilder body = new StringBuilder("{\"cases\":[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"inputs\":{},\"expectedRows\":[]}");
        }
        body.append("]}");
        mockMvc.perform(post("/api/dmn-decision-deployments/test")
                        .param("decisionKey", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())
                        .with(user("admin-cap", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void versions_andRedeploy_returnHistoryThenConflictWhenCurrent() throws Exception {
        String key = "apiHist" + System.nanoTime();
        deploy(key, "old");
        deploy(key, "new");

        mockMvc.perform(get("/api/dmn-decision-deployments/{key}/versions", key)
                        .with(user("admin-ver", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[0].deployedAt").isNotEmpty());

        mockMvc.perform(post("/api/dmn-decision-deployments/{key}/redeploy/{version}", key, 2)
                        .with(user("admin-rb", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/dmn-decision-deployments/{key}/redeploy/{version}", key, 1)
                        .with(user("admin-rb2", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deployedVersion").value(3));

        mockMvc.perform(get("/api/dmn-decision-deployments/{key}/versions", "missing" + System.nanoTime())
                        .with(user("admin-unknown", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private void deploy(String key, String output) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                key + ".dmn",
                "application/xml",
                DMN.formatted(key, output).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/dmn-decision-deployments")
                        .file(file)
                        .with(user("admin-deploy-" + key, "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());
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
