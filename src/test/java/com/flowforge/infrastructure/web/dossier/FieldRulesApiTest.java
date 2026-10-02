package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** D1 règles (TODO §80) — champs calculés, visibilité et obligation conditionnelles à travers la vraie chaîne HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class FieldRulesApiTest {

    private static final String TENANT_ID = "tenant_dev";

    private static final String DEFINITIONS = """
            [
              {"name": "pays", "label": "Pays", "type": "STRING", "required": false},
              {"name": "ht", "label": "Montant HT", "type": "NUMBER", "required": false},
              {"name": "taux", "label": "Taux", "type": "NUMBER", "required": false},
              {"name": "iban", "label": "IBAN", "type": "STRING", "required": false, "sensitive": true},
              {"name": "ttc", "label": "Montant TTC", "type": "NUMBER", "required": false,
               "formula": "dossier.fields.ht * (1 + dossier.fields.taux)"},
              {"name": "siret", "label": "SIRET", "type": "STRING", "required": true,
               "visibleWhen": "dossier.fields.pays = 'FR'"},
              {"name": "justificatif", "label": "Justificatif", "type": "STRING", "required": false,
               "requiredWhen": "dossier.fields.ht > 1000"}
            ]
            """;

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(t -> t.subject(userId).claim("tenant_id", TENANT_ID).claim("email", userId + "@example.com"))
                .authorities(Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String uniqueCode() {
        return "RULE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private ResultActions postType(String code, String definitions) throws Exception {
        return mockMvc.perform(post("/api/dossier-types")
                .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "label": "L %s", "workflowKey": "wf-%s", "active": true,
                         "allowUnknownFields": false, "fieldDefinitions": %s}
                        """.formatted(code, code, code.toLowerCase(), definitions)));
    }

    private String createType() throws Exception {
        String code = uniqueCode();
        postType(code, DEFINITIONS).andExpect(status().isCreated());
        return code;
    }

    private ResultActions createDossier(String code, String fields) throws Exception {
        return mockMvc.perform(post("/api/dossiers")
                .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dossierTypeCode\": \"%s\", \"label\": \"D\", \"fields\": %s}".formatted(code, fields)));
    }

    private static String extract(String json, String field) {
        var m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new AssertionError("Missing " + field + " in " + json);
        }
        return m.group(1);
    }

    @Test
    void theRulesRoundTripThroughTheDefinitionApi() throws Exception {
        String code = createType();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dossier-types/{code}", code)
                        .with(user("admin-" + code, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldDefinitions[4].formula").value("dossier.fields.ht * (1 + dossier.fields.taux)"))
                .andExpect(jsonPath("$.fieldDefinitions[5].visibleWhen").value("dossier.fields.pays = 'FR'"))
                .andExpect(jsonPath("$.fieldDefinitions[6].requiredWhen").value("dossier.fields.ht > 1000"))
                // Régression : une propriété dérivée (ex. « computed ») ne doit pas fuiter dans le JSON stocké puis relu.
                .andExpect(jsonPath("$.fieldDefinitions[4].computed").doesNotExist())
                .andExpect(jsonPath("$.fieldDefinitions[4].hasFormula").doesNotExist());
    }

    @Test
    void creatingADossierComputesTheDerivedFieldAndAppliesConditionalRules() throws Exception {
        String code = createType();

        // France : siret obligatoire (visible + required) ; HT > 1000 : justificatif obligatoire ; ttc calculé.
        createDossier(code, "{\"pays\": \"FR\", \"ht\": 2000, \"taux\": 0.2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='siret')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field=='justificatif')].code").value("REQUIRED"));

        createDossier(code, "{\"pays\": \"FR\", \"ht\": 2000, \"taux\": 0.2, \"siret\": \"12345678901234\", \"justificatif\": \"facture\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.ttc").value(2400.0));

        // Allemagne : siret sans objet, non obligatoire ; HT <= 1000 : justificatif facultatif.
        createDossier(code, "{\"pays\": \"DE\", \"ht\": 500, \"taux\": 0.1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.ttc").value(550.0))
                .andExpect(jsonPath("$.fields.siret").doesNotExist());
    }

    @Test
    void aComputedFieldCannotBeSuppliedAndAHiddenFieldCannotEitherAndNothingEchoesTheValue() throws Exception {
        String code = createType();

        createDossier(code, "{\"pays\": \"DE\", \"ht\": 1, \"taux\": 0, \"ttc\": 5, \"siret\": \"SECRET-VALUE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='ttc')].code").value("COMPUTED_READ_ONLY"))
                .andExpect(jsonPath("$.errors[?(@.field=='siret')].code").value("NOT_APPLICABLE"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("SECRET-VALUE"));
    }

    @Test
    void updatingAnInputRecomputesTheDerivedFieldAndClearsAFieldThatBecameNotApplicable() throws Exception {
        String code = createType();
        MvcResult created = createDossier(code, "{\"pays\": \"FR\", \"ht\": 100, \"taux\": 0.5, \"siret\": \"12345678901234\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fields.ttc").value(150.0)).andReturn();
        String id = extract(created.getResponse().getContentAsString(), "id");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", id)
                        .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\": {\"ht\": 200, \"pays\": \"DE\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.ttc").value(300.0))
                .andExpect(jsonPath("$.fields.siret").doesNotExist());

        mockMvc.perform(patch("/api/dossiers/{id}/fields", id)
                        .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\": {\"ttc\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("COMPUTED_READ_ONLY"));
    }

    @Test
    void anInvalidRuleIsRefusedAtDefinitionTime() throws Exception {
        for (String bad : new String[] {
                // syntaxe
                "[{\"name\":\"a\",\"label\":\"a\",\"type\":\"NUMBER\",\"required\":false},"
                        + "{\"name\":\"b\",\"label\":\"b\",\"type\":\"NUMBER\",\"required\":false,\"formula\":\"dossier.fields.a +\"}]",
                // construction interdite
                "[{\"name\":\"b\",\"label\":\"b\",\"type\":\"NUMBER\",\"required\":false,\"formula\":\"$eval('1')\"}]",
                // champ inconnu
                "[{\"name\":\"b\",\"label\":\"b\",\"type\":\"STRING\",\"required\":false,\"visibleWhen\":\"dossier.fields.nope = 1\"}]",
                // champ sensible lu
                "[{\"name\":\"i\",\"label\":\"i\",\"type\":\"STRING\",\"required\":false,\"sensitive\":true},"
                        + "{\"name\":\"b\",\"label\":\"b\",\"type\":\"STRING\",\"required\":false,\"visibleWhen\":\"dossier.fields.i = 'x'\"}]",
                // champ calculé obligatoire
                "[{\"name\":\"b\",\"label\":\"b\",\"type\":\"NUMBER\",\"required\":true,\"formula\":\"1\"}]"}) {
            postType(uniqueCode(), bad).andExpect(status().isBadRequest());
        }
    }

    @Test
    void thePreviewEndpointReturnsTheRulesOutcomeWithoutWritingAndIgnoresSensitiveInput() throws Exception {
        String code = createType();

        mockMvc.perform(post("/api/dossier-types/{code}/evaluate-rules", code)
                        .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\": {\"pays\": \"DE\", \"ht\": 2000, \"taux\": 0.25, \"iban\": \"FR76-NE-SERT-A-RIEN\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.computed.ttc").value(2500.0))
                .andExpect(jsonPath("$.hidden[0]").value("siret"))
                .andExpect(jsonPath("$.requiredByRule[0]").value("justificatif"))
                .andExpect(jsonPath("$.errors.length()").value(0))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("FR76"));
    }

    @Test
    void thePreviewNeedsTheRightToEditThisTypeAndAnAuthenticatedCaller() throws Exception {
        String code = createType();
        mockMvc.perform(post("/api/dossier-types/{code}/evaluate-rules", code)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fields\": {}}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/dossier-types/{code}/evaluate-rules", code)
                        .with(user("viewer-" + code, "DOSSIER_VIEWER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\": {}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTypeWithoutRulesPreviewsToAnEmptyOutcome() throws Exception {
        String code = uniqueCode();
        postType(code, "[{\"name\":\"a\",\"label\":\"a\",\"type\":\"STRING\",\"required\":false}]").andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossier-types/{code}/evaluate-rules", code)
                        .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\": {\"a\": \"x\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.computed").isEmpty())
                .andExpect(jsonPath("$.hidden").isEmpty());
    }
}
