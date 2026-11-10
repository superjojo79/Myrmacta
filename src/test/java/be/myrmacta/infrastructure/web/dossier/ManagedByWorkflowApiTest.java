package be.myrmacta.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.Arrays;
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

/**
 * TODO §126 (issue #9) — chaîne HTTP : drapeau {@code managedByWorkflow}, refus à la création et au
 * {@code PATCH .../fields} (D-MANAGED-HTTP).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ManagedByWorkflowApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired private MockMvc mockMvc;

    private static RequestPostProcessor admin() {
        return oidcLogin()
                .idToken(t -> t.subject("admin-managed").claim("tenant_id", TENANT_ID).claim("email", "a@example.com"))
                .authorities(Arrays.stream(new String[] {"TENANT_ADMIN"})
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private String createType() throws Exception {
        String code = "MNGAPI_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "label": "L", "workflowKey": "wf", "active": true, "allowUnknownFields": false,
                                 "fieldDefinitions": [
                                   {"name": "nom", "label": "Nom", "type": "STRING", "required": true},
                                   {"name": "identifiantIam", "label": "IAM", "type": "STRING", "required": false,
                                    "managedByWorkflow": true}
                                 ]}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldDefinitions[1].managedByWorkflow").value(true));
        return code;
    }

    private static String extract(String json, String field) {
        var m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new AssertionError("Missing " + field + " in " + json);
        }
        return m.group(1);
    }

    @Test
    void theFlagRoundTripsAndHumanWritesAreRejected() throws Exception {
        String code = createType();
        mockMvc.perform(get("/api/dossier-types/{code}", code).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldDefinitions[?(@.name=='identifiantIam')].managedByWorkflow").value(true));

        mockMvc.perform(post("/api/dossiers")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dossierTypeCode": "%s", "label": "D",
                                 "fields": {"nom": "Dupont", "identifiantIam": "forged"}}
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='identifiantIam')].code").value("MANAGED_BY_WORKFLOW"));

        String body = mockMvc.perform(post("/api/dossiers")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dossierTypeCode": "%s", "label": "D", "fields": {"nom": "Dupont"}}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = extract(body, "id");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\": {\"identifiantIam\": \"forged\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='identifiantIam')].code").value("MANAGED_BY_WORKFLOW"));

        mockMvc.perform(patch("/api/dossiers/{id}/fields", id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\": {\"nom\": \"Martin\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.nom").value("Martin"));
    }

    @Test
    void schemaRejectsManagedTogetherWithRequired() throws Exception {
        String code = "MNGBAD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "label": "L", "workflowKey": "wf", "active": true, "allowUnknownFields": false,
                                 "fieldDefinitions": [
                                   {"name": "id", "label": "Id", "type": "STRING", "required": true, "managedByWorkflow": true}
                                 ]}
                                """.formatted(code)))
                .andExpect(status().isBadRequest());
    }
}
