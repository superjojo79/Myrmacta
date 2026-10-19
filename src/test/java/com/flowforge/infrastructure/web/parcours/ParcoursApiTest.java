package com.flowforge.infrastructure.web.parcours;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
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

/** TODO §88 (LC5) — API des parcours : cycle de vie, compilation en brouillon, codes d'erreur, rôles. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ParcoursApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static String key() {
        return "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String body(String schemaKey, Integer expectedVersion, String steps) {
        return """
                {"label":"Sinistre","expectedVersion":%s,"definition":{
                  "statuses":[{"code":"OUVERT","label":"Ouvert","initial":true,"outcome":"NONE"},
                              {"code":"VALIDE","label":"Validé","initial":false,"outcome":"COMPLETED"}],
                  "allowedDossierStatuses":["OUVERT","EN_COURS"],
                  "onStartDossierStatus":null,
                  "steps":%s}}
                """.formatted(expectedVersion, steps.replace("SCHEMA", schemaKey));
    }

    private static final String TWO_STEPS = """
            [{"id":"saisie","kind":"QUESTION","params":{"schemaKey":"SCHEMA"}},
             {"id":"cloture","kind":"SET_WORKFLOW_STATUS","params":{"targetWorkflowStatus":"VALIDE"}}]""";

    private void createSchema(String schemaKey) throws Exception {
        mockMvc.perform(post("/api/task-question-schemas").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaKey":"%s","label":"Avis","fieldDefinitions":[{"name":"avis","label":"Avis","type":"STRING",
                                "required":true,"options":null,"group":null,"sensitive":false}]}
                                """.formatted(schemaKey)))
                .andExpect(status().isCreated());
    }

    @Test
    void createListGetUpdateAndDelete() throws Exception {
        String schema = "s" + key();
        createSchema(schema);
        String key = key();
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(schema, null, TWO_STEPS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/parcours").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].key", hasItem(key)));
        mockMvc.perform(get("/api/parcours/{key}", key).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.definition.steps.length()").value(2));
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(schema, 1, TWO_STEPS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(delete("/api/parcours/{key}", key).with(admin()).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/parcours/{key}", key).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void theDossierTypeTheParcoursWorksOnIsKeptAndAHostileCodeIsRefused() throws Exception {
        String schema = "s" + key();
        createSchema(schema);
        String key = key();
        String withType = body(schema, null, TWO_STEPS).replace("\"onStartDossierStatus\":null,",
                "\"onStartDossierStatus\":null,\"dossierTypeCode\":\"PERMIS_RIVERAIN\",");
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(withType))
                .andExpect(status().isOk()).andExpect(jsonPath("$.definition.dossierTypeCode").value("PERMIS_RIVERAIN"));
        mockMvc.perform(get("/api/parcours/{key}", key).with(admin()))
                .andExpect(jsonPath("$.definition.dossierTypeCode").value("PERMIS_RIVERAIN"));
        mockMvc.perform(put("/api/parcours/{key}", key()).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(withType.replace("PERMIS_RIVERAIN", "a b\\\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("dossier type code")));
    }

    @Test
    void staleVersionAndDuplicateCreationAreConflicts() throws Exception {
        String schema = "s" + key();
        createSchema(schema);
        String key = key();
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(schema, null, TWO_STEPS))).andExpect(status().isOk());
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(schema, null, TWO_STEPS))).andExpect(status().isConflict());
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(schema, 7, TWO_STEPS))).andExpect(status().isConflict());
    }

    @Test
    void anInconsistentParcoursIsRefusedWithTheStepNamed() throws Exception {
        String steps = """
                [{"id":"a","kind":"QUESTION","params":{"schemaKey":"x"},"next":"nowhere"}]""";
        mockMvc.perform(put("/api/parcours/{key}", key()).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, steps)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("nowhere")));
    }

    @Test
    void compileDryRunWritesNothingThenCompileDepositsADraft() throws Exception {
        String schema = "s" + key();
        createSchema(schema);
        String key = key();
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(schema, null, TWO_STEPS))).andExpect(status().isOk());
        mockMvc.perform(post("/api/parcours/{key}/compile", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftWritten").value(false))
                .andExpect(jsonPath("$.xml", containsString("<startEvent")));
        mockMvc.perform(post("/api/parcours/{key}/compile", key).with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftWritten").value(true))
                .andExpect(jsonPath("$.sha256").isString());
        mockMvc.perform(get("/api/parcours/{key}", key).with(admin()))
                .andExpect(jsonPath("$.lastCompiledSha256").isString());
    }

    @Test
    void aParcoursUsingADraftTemplateCompilesIntoADraftAndWarnsThatPublicationAwaitsActivation() throws Exception {
        // TODO §93 D-DRAFT-REF-STATUS : un modèle encore en brouillon n'empêche plus de déposer le brouillon du workflow.
        String template = "t" + key();
        mockMvc.perform(post("/api/document-templates").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateKey":"%s","variantKey":null,"locale":null,"label":"Décision",
                                "htmlContent":"<p>Décision</p>"}""".formatted(template)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));
        String key = key();
        String steps = """
                [{"id":"generer","kind":"DOCUMENT","params":{"documentType":"%s"}},
                 {"id":"cloture","kind":"SET_WORKFLOW_STATUS","params":{"targetWorkflowStatus":"VALIDE"}}]""".formatted(template);
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("x", null, steps))).andExpect(status().isOk());

        mockMvc.perform(post("/api/parcours/{key}/compile", key).with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftWritten").value(true))
                .andExpect(jsonPath("$.warnings", hasItem(containsString(template))));
    }

    @Test
    void compilingAnUnknownParcoursIs404AndPreviewNeverWrites() throws Exception {
        mockMvc.perform(post("/api/parcours/{key}/compile", key()).with(admin()).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/parcours/preview").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"apercu\",\"label\":\"Aperçu\",\"definition\":"
                                + body("x", null, TWO_STEPS).replaceAll("(?s)^.*\"definition\":", "").replaceAll("\\}\\s*$", "") + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftWritten").value(false));
        mockMvc.perform(get("/api/parcours/{key}", "apercu").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void previewSurfacesWhyTheDeploymentWouldRefuseTheBpmnWithoutWritingAnything() throws Exception {
        String steps = """
                [{"id":"saisie","kind":"DOCUMENT","params":{"documentType":"modeleInexistant"}},
                 {"id":"cloture","kind":"SET_WORKFLOW_STATUS","params":{"targetWorkflowStatus":"VALIDE"}}]""";
        String definition = body("x", null, steps).replaceAll("(?s)^.*\"definition\":", "").replaceAll("\\}\\s*$", "");
        mockMvc.perform(post("/api/parcours/preview").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"apercu\",\"label\":\"A\",\"definition\":" + definition + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.xml", containsString("<startEvent")))
                .andExpect(jsonPath("$.problem").isString());
    }

    @Test
    void aHostileKeyOrValueIsRefusedWithAMessageThatNamesTheCause() throws Exception {
        String definition = body("x", null, TWO_STEPS).replaceAll("(?s)^.*\"definition\":", "").replaceAll("\\}\\s*$", "");
        mockMvc.perform(post("/api/parcours/preview").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"p\\\" x=\\\"y\",\"label\":\"A\",\"definition\":" + definition + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("process key")));
        String hostile = """
                [{"id":"a","kind":"DECISION","params":{},"defaultNext":"a",
                  "branches":[{"variable":"x","operator":"EQ","value":"-1","next":"a"}]}]""";
        mockMvc.perform(put("/api/parcours/{key}", key()).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, hostile)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("negative and decimal")));
    }

    @Test
    void aDeleteWithAStaleVersionIsAConflictAndTheParcoursSurvives() throws Exception {
        String schema = "s" + key();
        createSchema(schema);
        String key = key();
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(schema, null, TWO_STEPS))).andExpect(status().isOk());
        mockMvc.perform(put("/api/parcours/{key}", key).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(schema, 1, TWO_STEPS))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/parcours/{key}", key).param("expectedVersion", "1").with(admin()).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/parcours/{key}", key).with(admin())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/parcours/{key}", key).param("expectedVersion", "2").with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/parcours/{key}", key).param("expectedVersion", "2").with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyDesignRolesMayUseParcours() throws Exception {
        mockMvc.perform(get("/api/parcours").with(role("DOSSIER_AGENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parcours").with(role("WORKFLOW_ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/parcours")).andExpect(status().is4xxClientError());
    }

    private static RequestPostProcessor admin() {
        return role("TENANT_ADMIN");
    }

    private static RequestPostProcessor role(String role) {
        return oidcLogin().idToken(t -> t.subject("parcours-" + role).claim("tenant_id", "tenant_dev"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
