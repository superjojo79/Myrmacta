package be.myrmacta.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * TODO §81 (D2) — contrat HTTP de la recherche sur champs dynamiques. Base H2 : les refus de validation (400) sont testés
 * ici ; la requête elle-même (jsonb) l'est sur PostgreSQL dans {@code DossierFieldSearchPostgresIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DossierFieldSearchApiTest {

    private static final String DEFINITIONS = """
            [
              {"name": "contrat", "label": "Contrat", "type": "STRING", "required": false, "searchable": true},
              {"name": "montant", "label": "Montant", "type": "NUMBER", "required": false, "searchable": true},
              {"name": "note", "label": "Note", "type": "STRING", "required": false},
              {"name": "iban", "label": "IBAN", "type": "STRING", "required": false, "sensitive": true}
            ]
            """;

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor admin() {
        return oidcLogin()
                .idToken(t -> t.subject("admin-d2").claim("tenant_id", "tenant_dev").claim("email", "a@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }

    private String type() throws Exception {
        String code = "SRCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "label": "L", "workflowKey": "wf-%s", "active": true,
                                 "allowUnknownFields": false, "fieldDefinitions": %s}
                                """.formatted(code, code.toLowerCase(), DEFINITIONS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldDefinitions[0].searchable").value(true))
                .andExpect(jsonPath("$.fieldDefinitions[2].searchable").value(false));
        return code;
    }

    @Test
    void aSensitiveOrStructuredFieldCannotBeDeclaredSearchable() throws Exception {
        for (String bad : new String[] {
            "{\"name\":\"x\",\"label\":\"x\",\"type\":\"STRING\",\"sensitive\":true,\"searchable\":true}",
            "{\"name\":\"x\",\"label\":\"x\",\"type\":\"MONEY\",\"searchable\":true}"
        }) {
            mockMvc.perform(post("/api/dossier-types").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code": "BAD_%s", "label": "L", "workflowKey": "wf", "active": true,
                                     "allowUnknownFields": false, "fieldDefinitions": [%s]}
                                    """.formatted(UUID.randomUUID().toString().substring(0, 6).toUpperCase(), bad)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void invalidFilterIsA400WithAClearReasonNeverAnEmptyResult() throws Exception {
        String code = type();
        mockMvc.perform(get("/api/dossiers").param("dossierTypeCode", code).param("f.note", "x").with(admin()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not searchable")));
        mockMvc.perform(get("/api/dossiers").param("dossierTypeCode", code).param("f.iban", "x").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dossiers").param("dossierTypeCode", code).param("f.inconnu", "x").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dossiers").param("dossierTypeCode", code).param("f.montant.gte", "abc").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dossiers").param("dossierTypeCode", code).param("f.montant.between", "1").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dossiers").param("f.contrat", "C-1").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("dossierTypeCode")));
        var req = get("/api/dossiers").param("dossierTypeCode", code).with(admin());
        for (int i = 0; i < 6; i++) {
            req = req.param("f.contrat.prefix", "a");
        }
        // un paramètre répété n'ajoute pas de critère : la limite se teste avec des clés distinctes
        var many = get("/api/dossiers").param("dossierTypeCode", code).with(admin());
        for (String k : new String[] {"f.contrat", "f.contrat.in", "f.contrat.prefix", "f.montant", "f.montant.gte", "f.montant.lte"}) {
            many = many.param(k, "1");
        }
        mockMvc.perform(many).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("at most 5")));
    }

    @Test
    void aValidFieldFilterRunsOnPostgresql() throws Exception {
        // Plus de H2 : la recherche jsonb s'exécute toujours (aucun refus « base non supportée »).
        String code = type();
        mockMvc.perform(get("/api/dossiers").param("dossierTypeCode", code).param("f.contrat", "C-1").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void withoutFieldFiltersTheListIsUnchanged() throws Exception {
        String code = type();
        mockMvc.perform(get("/api/dossiers").param("dossierTypeCode", code).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
