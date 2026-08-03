package com.flowforge.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DossierApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createDossier_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/dossiers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "CLAIM",
                                  "label": "Claim A",
                                  "fields": {}
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createType_tenantAdmin_returns201() throws Exception {
        String code = uniqueCode("TYPE");

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-create-type", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.label").value("Label " + code))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateType_tenantAdminCanPatchLabelAndActive() throws Exception {
        String code = createType(true);

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("admin-patch-type", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Patched %s",
                                  "active": false
                                }
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.label").value("Patched " + code))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void createType_workflowAdmin_returns403ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("workflow-admin", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(uniqueCode("DENIED"), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void createType_dossierManager_returns403ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("manager-denied", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(uniqueCode("MGRDENY"), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void createType_withSensitiveField_roundTripsFlag() throws Exception {
        String code = uniqueCode("SENS");
        String fields = """
                [{
                  "name": "ssn",
                  "label": "SSN",
                  "type": "STRING",
                  "required": false,
                  "options": [],
                  "sensitive": true
                }]
                """;

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-sens", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fields)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldDefinitions[0].sensitive").value(true));

        mockMvc.perform(get("/api/dossier-types/{code}", code)
                        .with(user("admin-sens", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldDefinitions[0].name").value("ssn"))
                .andExpect(jsonPath("$.fieldDefinitions[0].sensitive").value(true));
    }

    @Test
    void createType_withoutSensitiveKey_defaultsToFalse() throws Exception {
        String code = uniqueCode("NOSENS");
        String fields = """
                [{
                  "name": "city",
                  "label": "City",
                  "type": "STRING",
                  "required": false,
                  "options": []
                }]
                """;

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-nosens", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fields)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldDefinitions[0].sensitive").value(false));
    }

    @Test
    void getDossier_sensitiveFieldValue_isReturnedInClear() throws Exception {
        String code = uniqueCode("SSNTYPE");
        String fields = """
                [{
                  "name": "ssn",
                  "label": "SSN",
                  "type": "STRING",
                  "required": true,
                  "options": [],
                  "sensitive": true
                }]
                """;
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-ssn", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fields)))
                .andExpect(status().isCreated());
        grant(code, "manager-ssn", "EDIT");

        MvcResult created = mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-ssn"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Sensitive dossier", "{\"ssn\":\"123-45-6789\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.ssn").value("123-45-6789"))
                .andReturn();
        String id = extractString(created.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/dossiers/{id}", id).with(user("manager-ssn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.ssn").value("123-45-6789"));
    }

    @Test
    void createDossier_managerWithEditAcl_returns201Draft() throws Exception {
        String code = createType(true);
        grant(code, "manager-create", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-create"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Manager dossier")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.dossierTypeCode").value(code))
                .andExpect(jsonPath("$.label").value("Manager dossier"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value("manager-create"))
                .andExpect(jsonPath("$.updatedBy").value("manager-create"));
    }

    @Test
    void createDossier_appliesFieldDefaultWhenFieldIsMissing() throws Exception {
        String code = createTypeWithFields("DEFAULT", """
                [
                  {
                    "name": "priority",
                    "label": "Priority",
                    "type": "STRING",
                    "required": false,
                    "defaultValue": "NORMAL",
                    "options": [],
                    "group": "details"
                  }
                ]
                """);
        grant(code, "manager-default", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-default"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Default dossier")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.priority").value("NORMAL"));
    }

    @Test
    void createDossier_withInvalidFieldTypeReturns400WithErrors() throws Exception {
        String code = createTypeWithFields("INVALID", """
                [
                  {
                    "name": "amount",
                    "label": "Amount",
                    "type": "NUMBER",
                    "required": true,
                    "defaultValue": null,
                    "options": [],
                    "group": "details"
                  }
                ]
                """);
        grant(code, "manager-invalid-field", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-invalid-field"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Invalid field dossier", """
                                {
                                  "amount": "not-a-number"
                                }
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"));
    }

    @Test
    void createDossier_withoutLabel_returns400() throws Exception {
        String code = createType(true);
        grant(code, "manager-missing-label", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-missing-label"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "fields": {}
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createDossier_inactiveType_returns409() throws Exception {
        String code = createType(false);
        grant(code, "manager-inactive", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-inactive"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Inactive type dossier")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getDossier_withoutViewAcl_returns403() throws Exception {
        String code = createType(true);
        grant(code, "manager-owner", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-owner", "Owner dossier");

        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("no-view-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDossier_withViewGrant_returns200() throws Exception {
        String code = createType(true);
        grant(code, "manager-view-source", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-view-source", "Viewable dossier");
        grant(code, "viewer-with-grant", "VIEW");

        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("viewer-with-grant", "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.dossierTypeCode").value(code));
    }

    @Test
    void getDossier_workflowAdminWithoutGrant_returns200() throws Exception {
        String code = createType(true);
        grant(code, "manager-workflow-bypass-source", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-workflow-bypass-source", "Workflow bypass dossier");

        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("workflow-bypass", "WORKFLOW_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()));
    }

    @Test
    void revokeAcl_thenCreateAndGetAreDenied() throws Exception {
        String code = createType(true);
        grant(code, "revoked-manager", "EDIT");
        DossierFixture dossier = createDossier(code, "revoked-manager", "Revoked dossier");

        revoke(code, "revoked-manager");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("revoked-manager"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Denied after revoke")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("revoked-manager")))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantAcl_twiceIsIdempotent() throws Exception {
        String code = createType(true);

        grant(code, "idempotent-manager", "EDIT");
        grant(code, "idempotent-manager", "EDIT");
    }

    @Test
    void grantAcl_dossierManager_returns403ProblemDetail() throws Exception {
        String code = createType(true);

        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("manager-acl", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "someone",
                                  "permission": "VIEW"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void updateStatus_viewerWithViewOnly_returns403() throws Exception {
        String code = createType(true);
        grant(code, "setup-editor", "EDIT");
        grant(code, "viewer-status", "VIEW");
        DossierFixture dossier = createDossier(code, "setup-editor", "Viewer status dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("viewer-status", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "OUVERT"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void updateStatus_legalTransition_returnsUpdatedDossier() throws Exception {
        String code = createType(true);
        grant(code, "manager-status", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-status", "Status dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("manager-status"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "OUVERT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.status").value("OUVERT"))
                .andExpect(jsonPath("$.updatedBy").value("manager-status"))
                .andExpect(jsonPath("$.version").value(dossier.version() + 1));
    }

    @Test
    void createType_duplicateCode_returns409() throws Exception {
        String code = createType(true);

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-dup", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Unique constraint or data integrity violation"));
    }

    @Test
    void grantAcl_unknownType_returns404() throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", "MISSING_TYPE")
                        .with(user("acl-admin-missing", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "someone",
                                  "permission": "VIEW"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDossier_byReference_returns200() throws Exception {
        String code = createType(true);
        grant(code, "manager-ref", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-ref", "Ref dossier");

        mockMvc.perform(get("/api/dossiers").param("ref", dossier.reference())
                        .with(user("manager-ref")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.reference").value(dossier.reference()));
    }

    @Test
    void updateStatus_staleExpectedVersion_returns409() throws Exception {
        String code = createType(true);
        grant(code, "manager-version", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-version", "Version dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("manager-version"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "OUVERT",
                                  "expectedVersion": %d
                                }
                                """.formatted(dossier.version() + 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateStatus_illegalTransition_returns409() throws Exception {
        String code = createType(true);
        grant(code, "manager-illegal", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-illegal", "Illegal transition");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("manager-illegal"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ARCHIVE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createType_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-no-csrf", "TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(uniqueCode("CSRF"), true)))
                .andExpect(status().isForbidden());
    }

    private String createType(boolean active) throws Exception {
        String code = uniqueCode(active ? "ACTIVE" : "INACTIVE");
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, active)))
                .andExpect(status().isCreated());
        return code;
    }

    private String createTypeWithFields(String prefix, String fieldDefinitions) throws Exception {
        String code = uniqueCode(prefix);
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fieldDefinitions)))
                .andExpect(status().isCreated());
        return code;
    }

    private void grant(String code, String userId, String permission) throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "%s",
                                  "permission": "%s"
                                }
                                """.formatted(userId, permission)))
                .andExpect(status().isNoContent());
    }

    private void revoke(String code, String userId) throws Exception {
        mockMvc.perform(delete("/api/dossier-types/{code}/acl/{userId}", code, userId)
                        .with(user("acl-admin-" + code, "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private DossierFixture createDossier(String code, String userId, String label) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/dossiers")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, label)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new DossierFixture(
                extractString(body, "id"),
                extractString(body, "reference"),
                extractLong(body, "version"));
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", TENANT_ID)
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String typePayload(String code, boolean active) {
        return typePayload(code, active, "[]");
    }

    private static String typePayload(String code, boolean active, String fieldDefinitions) {
        return """
                {
                  "code": "%s",
                  "label": "Label %s",
                  "workflowKey": "workflow-%s",
                  "active": %s,
                  "allowUnknownFields": false,
                  "fieldDefinitions": %s
                }
                """.formatted(code, code, code.toLowerCase(), active, fieldDefinitions);
    }

    private static String dossierPayload(String code, String label) {
        return dossierPayload(code, label, "{}");
    }

    private static String dossierPayload(String code, String label, String fields) {
        return """
                {
                  "dossierTypeCode": "%s",
                  "label": "%s",
                  "fields": %s
                }
                """.formatted(code, label, fields);
    }

    private static String uniqueCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String extractString(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + field + " in " + json);
        }
        return matcher.group(1);
    }

    private static long extractLong(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + field + " in " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private record DossierFixture(String id, String reference, long version) {}
}
