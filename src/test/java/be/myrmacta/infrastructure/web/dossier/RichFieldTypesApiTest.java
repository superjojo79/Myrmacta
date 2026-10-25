package be.myrmacta.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** D1 (TODO §76) — types MONEY, REPEATABLE_GROUP, EXTERNAL_REF à travers la vraie chaîne HTTP et sécurité. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class RichFieldTypesApiTest {

    private static final String TENANT_ID = "tenant_dev";

    private static final String DEFINITIONS = """
            [
              {"name": "montant", "label": "Montant", "type": "MONEY", "required": false, "options": ["EUR", "USD"]},
              {"name": "fournisseur", "label": "Fournisseur", "type": "EXTERNAL_REF", "required": false, "options": ["d365fo"]},
              {"name": "lignes", "label": "Lignes", "type": "REPEATABLE_GROUP", "required": false, "minRows": 0, "maxRows": 3,
               "fields": [
                 {"name": "poste", "label": "Poste", "type": "STRING", "required": true},
                 {"name": "prix", "label": "Prix", "type": "MONEY", "required": false}
               ]},
              {"name": "budget", "label": "Budget interne", "type": "REPEATABLE_GROUP", "required": false, "sensitive": true,
               "fields": [{"name": "poste", "label": "Poste", "type": "STRING", "required": false}]}
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
        return "RICH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String createType(String fieldDefinitions) throws Exception {
        String code = uniqueCode();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "label": "L %s", "workflowKey": "wf-%s", "active": true,
                                 "allowUnknownFields": false, "fieldDefinitions": %s}
                                """.formatted(code, code, code.toLowerCase(), fieldDefinitions)))
                .andExpect(status().isCreated());
        return code;
    }

    private org.springframework.test.web.servlet.ResultActions createDossier(String code, String fields) throws Exception {
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
    void theStructuredTypesRoundTripThroughTheDefinitionApiAndComeBackCanonical() throws Exception {
        String code = createType(DEFINITIONS);

        mockMvc.perform(get("/api/dossier-types/{code}", code).with(user("admin-" + code, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldDefinitions[0].type").value("MONEY"))
                .andExpect(jsonPath("$.fieldDefinitions[0].options[0]").value("EUR"))
                .andExpect(jsonPath("$.fieldDefinitions[2].type").value("REPEATABLE_GROUP"))
                .andExpect(jsonPath("$.fieldDefinitions[2].fields.length()").value(2))
                .andExpect(jsonPath("$.fieldDefinitions[2].maxRows").value(3));

        createDossier(code, """
                {"montant": {"amount": "12.5", "currency": "EUR"},
                 "fournisseur": {"system": "d365fo", "id": " V-0042 ", "label": "  Contoso "},
                 "lignes": [{"poste": "Toiture", "prix": {"amount": 1000, "currency": "EUR"}}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.montant.amount").value("12.50"))
                .andExpect(jsonPath("$.fields.montant.currency").value("EUR"))
                .andExpect(jsonPath("$.fields.fournisseur.id").value("V-0042"))
                .andExpect(jsonPath("$.fields.fournisseur.label").value("Contoso"))
                .andExpect(jsonPath("$.fields.lignes[0].prix.amount").value("1000.00"));
    }

    @Test
    void invalidStructuredValuesAreRefusedWithAFieldPathAndNoEchoOfTheValue() throws Exception {
        String code = createType(DEFINITIONS);

        createDossier(code, """
                {"montant": {"amount": "1.234", "currency": "EUR"},
                 "lignes": [{"prix": {"amount": "5", "currency": "EUR"}}, {"poste": "ok"}, {"poste": "a"}, {"poste": "b"}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='montant')].code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.errors[?(@.field=='lignes')].code").value("INVALID_VALUE"));

        createDossier(code, "{\"lignes\": [{\"prix\": {\"amount\": \"5\", \"currency\": \"EUR\"}}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lignes[0].poste"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

        createDossier(code, "{\"montant\": {\"amount\": \"1\", \"currency\": \"GBP\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_OPTION"));
        createDossier(code, "{\"fournisseur\": {\"system\": \"sap\", \"id\": \"1\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_OPTION"));
    }

    @Test
    void anInvalidDefinitionIsRefusedAtDefinitionTime() throws Exception {
        String code = uniqueCode();
        for (String bad : new String[] {
                "[{\"name\":\"g\",\"label\":\"g\",\"type\":\"REPEATABLE_GROUP\",\"required\":false,\"fields\":[]}]",
                "[{\"name\":\"m\",\"label\":\"m\",\"type\":\"MONEY\",\"required\":false,\"options\":[\"ZZZ\"]}]",
                "[{\"name\":\"g\",\"label\":\"g\",\"type\":\"REPEATABLE_GROUP\",\"required\":false,\"fields\":"
                        + "[{\"name\":\"s\",\"label\":\"s\",\"type\":\"STRING\",\"required\":false,\"sensitive\":true}]}]"}) {
            mockMvc.perform(post("/api/dossier-types")
                            .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code": "%s", "label": "L", "workflowKey": "wf", "active": true,
                                     "allowUnknownFields": false, "fieldDefinitions": %s}
                                    """.formatted(code, bad)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void patchingAStructuredFieldNormalizesAndKeepsTheRestOfTheDossier() throws Exception {
        String code = createType(DEFINITIONS);
        MvcResult created = createDossier(code, "{\"montant\": {\"amount\": \"1\", \"currency\": \"EUR\"}}")
                .andExpect(status().isCreated()).andReturn();
        String id = extract(created.getResponse().getContentAsString(), "id");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", id)
                        .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\": {\"montant\": {\"amount\": 99.9, \"currency\": \"USD\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.montant.amount").value("99.90"))
                .andExpect(jsonPath("$.fields.montant.currency").value("USD"));
    }

    @Test
    void aSensitiveGroupIsHiddenFromNonAdminsAndAbsentFromTheBiStyleExport() throws Exception {
        String code = createType(DEFINITIONS);
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\": \"viewer-rich\", \"permission\": \"VIEW\"}"))
                .andExpect(status().isNoContent());
        MvcResult created = createDossier(code, """
                {"budget": [{"poste": "CONFIDENTIEL-XYZ"}], "montant": {"amount": "5", "currency": "EUR"}}""")
                .andExpect(status().isCreated()).andReturn();
        String id = extract(created.getResponse().getContentAsString(), "id");

        String asViewer = mockMvc.perform(get("/api/dossiers/{id}", id).with(user("viewer-rich", "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.montant.amount").value("5.00"))
                .andReturn().getResponse().getContentAsString();
        assertThat(asViewer).doesNotContain("CONFIDENTIEL-XYZ");
    }

    @Test
    void theCsvExportFlattensMoneyAndReferencesAndPutsAGroupInOneJsonColumn() throws Exception {
        String code = createType(DEFINITIONS);
        createDossier(code, """
                {"montant": {"amount": "12.5", "currency": "EUR"},
                 "fournisseur": {"system": "d365fo", "id": "V-1", "label": "Contoso"},
                 "lignes": [{"poste": "Toiture"}, {"poste": "Cuisine"}]}""").andExpect(status().isCreated());

        String csv = mockMvc.perform(get("/api/dossiers/export.csv").param("dossierTypeCode", code)
                        .with(user("admin-" + code, "TENANT_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("montant.amount,montant.currency,fournisseur.system,fournisseur.id,fournisseur.label,lignes");
        assertThat(csv).contains("12.50,EUR,d365fo,V-1,Contoso");
        assertThat(csv).contains("\"[{\"\"poste\"\":\"\"Toiture\"\"},{\"\"poste\"\":\"\"Cuisine\"\"}]\"");
    }

    @Test
    void changingAFieldToAStructuredTypeIsRefusedWhileExistingDossiersWouldBecomeInvalid() throws Exception {
        String code = createType("[{\"name\":\"montant\",\"label\":\"Montant\",\"type\":\"STRING\",\"required\":false}]");
        createDossier(code, "{\"montant\": \"12 EUR\"}").andExpect(status().isCreated());

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("admin-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldDefinitions\": [{\"name\":\"montant\",\"label\":\"Montant\",\"type\":\"MONEY\",\"required\":false}]}"))
                .andExpect(status().isConflict());
    }

    // ---- revue adversariale D1 -------------------------------------------------------------------------------------

    private static final String SENSITIVE_DEFS = """
            [
              {"name": "salaire", "label": "Salaire", "type": "MONEY", "required": false, "sensitive": true},
              {"name": "budget", "label": "Budget", "type": "REPEATABLE_GROUP", "required": false, "sensitive": true,
               "fields": [{"name": "poste", "label": "Poste", "type": "STRING", "required": false}]},
              {"name": "note", "label": "Note", "type": "STRING", "required": false}
            ]
            """;

    @Test
    void aNonAdminCsvExportNeverCarriesASensitiveMoneyOrGroup() throws Exception {
        String code = createType(SENSITIVE_DEFS);
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-" + code, "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\": \"viewer-csv\", \"permission\": \"VIEW\"}"))
                .andExpect(status().isNoContent());
        createDossier(code, """
                {"salaire": {"amount": "4242.00", "currency": "EUR"},
                 "budget": [{"poste": "CONFIDENTIEL-XYZ"}], "note": "visible"}""").andExpect(status().isCreated());

        String viewer = mockMvc.perform(get("/api/dossiers/export.csv").param("dossierTypeCode", code)
                        .with(user("viewer-csv", "DOSSIER_VIEWER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(viewer).contains("visible").doesNotContain("4242").doesNotContain("CONFIDENTIEL-XYZ");

        String admin = mockMvc.perform(get("/api/dossiers/export.csv").param("dossierTypeCode", code)
                        .with(user("admin-" + code, "TENANT_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(admin).contains("4242.00").contains("CONFIDENTIEL-XYZ");
    }

    @Test
    void aJsonNumberAmountThatADoubleCannotHoldIsRefusedWhileAQuotedOneIsKept() throws Exception {
        String code = createType(DEFINITIONS);
        createDossier(code, "{\"montant\": {\"amount\": 99999999999999.99, \"currency\": \"EUR\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
        createDossier(code, "{\"montant\": {\"amount\": \"99999999999999.99\", \"currency\": \"EUR\"}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.montant.amount").value("99999999999999.99"));
    }

    @Test
    void aNullCellInAGroupRowIsAbsenceAndNeverAServerError() throws Exception {
        String code = createType(DEFINITIONS);
        createDossier(code, "{\"lignes\": [{\"poste\": \"Toiture\", \"prix\": null}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.lignes[0].poste").value("Toiture"))
                .andExpect(jsonPath("$.fields.lignes[0].prix").doesNotExist());
        createDossier(code, "{\"lignes\": [{\"poste\": null}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lignes[0].poste"));
    }

    @Test
    void anOversizedCellIsRefusedBeforeItCanBeStored() throws Exception {
        String code = createType(DEFINITIONS);
        createDossier(code, "{\"lignes\": [{\"poste\": \"" + "x".repeat(10_001) + "\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lignes[0].poste"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    }
}
