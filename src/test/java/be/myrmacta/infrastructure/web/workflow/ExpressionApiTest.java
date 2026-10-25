package be.myrmacta.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** LC2 (TODO §75) — API de validation et d'évaluation à blanc, à travers la vraie chaîne de sécurité. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ExpressionApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor as(String role) {
        return oidcLogin()
                .idToken(t -> t.subject("u-" + role).claim("tenant_id", "tenant_dev"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private org.springframework.test.web.servlet.ResultActions call(String path, String role, String json)
            throws Exception {
        return mockMvc.perform(post("/api/expressions/" + path)
                .with(as(role))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void evaluateRunsOnTheProvidedContextForBothEditorRoles() throws Exception {
        for (String role : new String[] {"WORKFLOW_ADMIN", "TENANT_ADMIN"}) {
            call("evaluate", role, """
                    {"expression":"dossier.fields.montant * 2","context":{"dossier":{"fields":{"montant":21}}}}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.defined").value(true))
                    .andExpect(jsonPath("$.value").value(42));
        }
    }

    @Test
    void anAbsentValueIsReportedAsNotDefined() throws Exception {
        call("evaluate", "TENANT_ADMIN", "{\"expression\":\"a.b\",\"context\":{}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defined").value(false));
    }

    @Test
    void validateAnswers200WithTheErrorSoAnEditorCanShowIt() throws Exception {
        call("validate", "TENANT_ADMIN", "{\"expression\":\"dossier.fields.montant >\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.position").isNumber());
        call("validate", "TENANT_ADMIN", "{\"expression\":\"$eval(\\\"1\\\")\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("$eval")));
        call("validate", "WORKFLOW_ADMIN", "{\"expression\":\"1 + 1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void evaluateRefusesAnInvalidExpressionOrAViolatedBoundWith400() throws Exception {
        call("evaluate", "TENANT_ADMIN", "{\"expression\":\"$random()\",\"context\":{}}")
                .andExpect(status().isBadRequest());
        call("evaluate", "TENANT_ADMIN", "{\"expression\":\"$pad(\\\"x\\\", 2000000000)\",\"context\":{}}")
                .andExpect(status().isBadRequest());
        call("evaluate", "TENANT_ADMIN", "{\"expression\":\"\",\"context\":{}}").andExpect(status().isBadRequest());
    }

    @Test
    void otherRolesAndAnonymousAreRefused() throws Exception {
        for (String path : new String[] {"evaluate", "validate"}) {
            call(path, "DOSSIER_MANAGER", "{\"expression\":\"1\"}").andExpect(status().isForbidden());
            call(path, "API_SERVICE", "{\"expression\":\"1\"}").andExpect(status().isForbidden());
            mockMvc.perform(post("/api/expressions/" + path)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expression\":\"1\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void postWithoutCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/expressions/evaluate")
                        .with(as("TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"1\"}"))
                .andExpect(status().isForbidden());
    }
}
