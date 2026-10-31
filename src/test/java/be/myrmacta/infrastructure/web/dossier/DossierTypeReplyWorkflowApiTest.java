package be.myrmacta.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — workflow « réponse » d'un type de dossier, par la vraie chaîne HTTP et sécurité. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DossierTypeReplyWorkflowApiTest {

    private static final String TENANT = "tenant_dev";

    @Autowired private MockMvc mockMvc;

    @Test
    void anAdminSetsReadsAndClearsTheReplyWorkflowOfAType() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String type = "FACT_" + suffix;
        String child = "REP_" + suffix;
        String process = "reponse" + suffix;
        type(type, process);
        type(child, process);
        mockMvc.perform(post("/api/workflow-type-configs").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowKey":"%s","label":"Réponse","onStartDossierStatus":"EN_COURS",
                                 "allowedDossierStatuses":["OUVERT","EN_COURS"],
                                 "statusDefinitions":[{"code":"OUVERT","label":"Ouvert","initial":true,"outcome":"NONE"}]}
                                """.formatted(process)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dossier-types/{code}/reply-workflow", type).with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/dossier-types/{code}/reply-workflow", type).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DOSSIER_ENFANT\",\"workflowKey\":\"" + process + "\",\"childTypeCode\":\"" + child + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DOSSIER_ENFANT"))
                .andExpect(jsonPath("$.childTypeCode").value(child));
        mockMvc.perform(put("/api/dossier-types/{code}/reply-workflow", type).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SUR_DOSSIER\",\"workflowKey\":\"" + process + "\",\"childTypeCode\":\"" + child + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childTypeCode").doesNotExist());
        mockMvc.perform(get("/api/dossier-types/{code}/reply-workflow", type).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SUR_DOSSIER"));

        mockMvc.perform(put("/api/dossier-types/{code}/reply-workflow", type).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SUR_DOSSIER\",\"workflowKey\":\"inconnu\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/dossier-types/{code}/reply-workflow", type).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"DOSSIER_ENFANT\",\"workflowKey\":\"" + process + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/dossier-types/{code}/reply-workflow", "INCONNU_" + suffix).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SUR_DOSSIER\",\"workflowKey\":\"" + process + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/dossier-types/{code}/reply-workflow", type).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/dossier-types/{code}/reply-workflow", type).with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void onlyATenantAdminTouchesTheReplyWorkflow() throws Exception {
        RequestPostProcessor manager = oidcLogin().idToken(token -> token.subject("agent").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_DOSSIER_MANAGER"));
        mockMvc.perform(get("/api/dossier-types/{code}/reply-workflow", "FACTURE").with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/dossier-types/{code}/reply-workflow", "FACTURE").with(manager).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SUR_DOSSIER\",\"workflowKey\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    private void type(String code, String process) throws Exception {
        mockMvc.perform(post("/api/dossier-types").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","label":"Type","workflowKey":"%s","active":true,"allowUnknownFields":false,
                                 "fieldDefinitions":[{"name":"objet","label":"Objet","type":"STRING","required":false}]}
                                """.formatted(code, process)))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.subject("admin-reponse").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }
}
