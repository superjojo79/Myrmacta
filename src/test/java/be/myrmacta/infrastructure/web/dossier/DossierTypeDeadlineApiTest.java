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

/** TODO §115 (D-DEADLINE-FIELD) — échéance d'un type de dossier, par la vraie chaîne HTTP et sécurité. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DossierTypeDeadlineApiTest {

    private static final String TENANT = "tenant_dev";

    @Autowired private MockMvc mockMvc;

    @Test
    void anAdminSetsReadsAndClearsTheDeadlineOfAType() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String type = "ECH_" + suffix;
        String group = "ESC_" + suffix;
        mockMvc.perform(post("/api/dossier-types").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","label":"Type","workflowKey":"wf","active":true,"allowUnknownFields":false,
                                 "fieldDefinitions":[{"name":"objet","label":"Objet","type":"STRING","required":false},
                                                     {"name":"echeance","label":"Échéance","type":"DATE","required":false}]}
                                """.formatted(type)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/user-groups").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + group + "\",\"label\":\"Escalade\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dossier-types/{code}/deadline", type).with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/dossier-types/{code}/deadline", type).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"echeance\",\"reminderDays\":5,\"escalationGroup\":\"" + group + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminderDays").value(5))
                .andExpect(jsonPath("$.escalationGroup").value(group));
        mockMvc.perform(get("/api/dossier-types/{code}/deadline", type).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldName").value("echeance"));

        mockMvc.perform(put("/api/dossier-types/{code}/deadline", type).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"objet\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/dossier-types/{code}/deadline", type).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"echeance\",\"escalationGroup\":\"INCONNU\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/dossier-types/{code}/deadline", type).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"echeance\",\"reminderDays\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/dossier-types/{code}/deadline", "INCONNU_" + suffix).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fieldName\":\"echeance\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/dossier-types/{code}/deadline", type).with(admin()).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/dossier-types/{code}/deadline", type).with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void onlyATenantAdminTouchesTheDeadline() throws Exception {
        RequestPostProcessor manager = oidcLogin().idToken(token -> token.subject("agent").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_DOSSIER_MANAGER"));
        mockMvc.perform(get("/api/dossier-types/{code}/deadline", "FACTURE").with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/dossier-types/{code}/deadline", "FACTURE").with(manager).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fieldName\":\"echeance\"}"))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.subject("admin-echeance").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }
}
