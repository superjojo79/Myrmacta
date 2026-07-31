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
    void createType_workflowAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("workflow-admin", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(uniqueCode("DENIED"), true)))
                .andExpect(status().isForbidden());
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
                .andExpect(jsonPath("$.updatedBy").value("manager-status"));
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
        return """
                {
                  "code": "%s",
                  "label": "Label %s",
                  "workflowKey": "workflow-%s",
                  "active": %s,
                  "allowUnknownFields": false,
                  "fieldDefinitions": []
                }
                """.formatted(code, code, code.toLowerCase(), active);
    }

    private static String dossierPayload(String code, String label) {
        return """
                {
                  "dossierTypeCode": "%s",
                  "label": "%s",
                  "fields": {}
                }
                """.formatted(code, label);
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
