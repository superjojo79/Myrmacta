package com.flowforge.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Arrays;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §102 (issue #184) — unicité à travers la vraie chaîne HTTP : déclaration, refus {@code UNIQUE} à la création et à la
 * correction (D-UNIQUE-HTTP : 400, {@code errors[].reference}), activation refusée en présence de doublons (409,
 * {@code duplicates}). Le cas de l'exemple riverain : clé calculée {@code plaqueNormalisee}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class UniqueFieldApiTest {

    private static final String TENANT_ID = "tenant_dev";

    private static final String PLAQUE = """
              {"name": "plaque", "label": "Plaque", "type": "STRING", "required": true}""";
    private static final String CLE = """
              {"name": "cle", "label": "Clé", "type": "STRING", "required": false, "searchable": true, "unique": %s,
               "formula": "$uppercase($replace($replace(dossier.fields.plaque, ' ', ''), '-', ''))"}""";

    @Autowired private MockMvc mockMvc;

    private static RequestPostProcessor admin() {
        return oidcLogin()
                .idToken(t -> t.subject("admin-unique").claim("tenant_id", TENANT_ID).claim("email", "a@example.com"))
                .authorities(Arrays.stream(new String[] {"TENANT_ADMIN"})
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toArray(SimpleGrantedAuthority[]::new));
    }

    private String createType(boolean unique) throws Exception {
        String code = "UNQAPI_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        mockMvc.perform(post("/api/dossier-types").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "label": "L", "workflowKey": "wf", "active": true, "allowUnknownFields": false,
                                 "fieldDefinitions": [%s, %s]}
                                """.formatted(code, PLAQUE, CLE.formatted(unique))))
                .andExpect(status().isCreated());
        return code;
    }

    private ResultActions createDossier(String code, String plaque) throws Exception {
        return mockMvc.perform(post("/api/dossiers").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dossierTypeCode\": \"%s\", \"label\": \"D\", \"fields\": {\"plaque\": \"%s\"}}"
                        .formatted(code, plaque)));
    }

    private static String extract(String json, String field) {
        var m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new AssertionError("Missing " + field + " in " + json);
        }
        return m.group(1);
    }

    @Test
    void theFlagRoundTripsThroughTheDefinitionApi() throws Exception {
        String code = createType(true);
        mockMvc.perform(get("/api/dossier-types/{code}", code).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldDefinitions[1].unique").value(true))
                .andExpect(jsonPath("$.fieldDefinitions[0].unique").value(false));
    }

    @Test
    void aSensitiveOrBooleanUniqueFieldIsRefusedAtDefinitionTime() throws Exception {
        for (String bad : new String[] {
                "{\"name\": \"s\", \"label\": \"S\", \"type\": \"STRING\", \"sensitive\": true, \"unique\": true}",
                "{\"name\": \"b\", \"label\": \"B\", \"type\": \"BOOLEAN\", \"unique\": true}"}) {
            mockMvc.perform(post("/api/dossier-types").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code": "UNQBAD_%s", "label": "L", "workflowKey": "wf", "active": true,
                                     "allowUnknownFields": false, "fieldDefinitions": [%s]}
                                    """.formatted(UUID.randomUUID().toString().substring(0, 8).toUpperCase(), bad)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aDuplicateIsRefusedWithTheExistingReferenceAtCreationAndCorrection_U8() throws Exception {
        String code = createType(true);
        String first = createDossier(code, "1 abc 123").andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        String firstReference = extract(first, "reference");

        createDossier(code, "1-ABC-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cle"))
                .andExpect(jsonPath("$.errors[0].code").value("UNIQUE"))
                .andExpect(jsonPath("$.errors[0].reference").value(firstReference))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("cle UNIQUE")))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("1ABC123"))));

        String other = createDossier(code, "2 xyz 9").andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        mockMvc.perform(patch("/api/dossiers/{id}/fields", extract(other, "id")).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\": {\"plaque\": \"1ABC 123\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UNIQUE"))
                .andExpect(jsonPath("$.errors[0].reference").value(firstReference));
    }

    @Test
    void enablingUniqueOverOpenDuplicatesIsRefusedWithTheDuplicates_U10() throws Exception {
        String code = createType(false);
        createDossier(code, "1 abc 123").andExpect(status().isCreated());
        createDossier(code, "1-abc-123").andExpect(status().isCreated());

        mockMvc.perform(patch("/api/dossier-types/{code}", code).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldDefinitions\": [%s, %s]}".formatted(PLAQUE, CLE.formatted(true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.incompatibleCount").value(2))
                .andExpect(jsonPath("$.duplicates[0].field").value("cle"))
                .andExpect(jsonPath("$.duplicates[0].value").value("1ABC123"))
                .andExpect(jsonPath("$.duplicates[0].references.length()").value(2));
    }
}
