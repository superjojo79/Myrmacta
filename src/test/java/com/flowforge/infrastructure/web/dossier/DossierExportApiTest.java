package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class DossierExportApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportCsv_tenantAdmin_seesSensitiveFieldInClear() throws Exception {
        String code = createTypeWithSensitiveField("EXP_ADMIN");
        createDossier(code, "admin-export-1", fieldsJson("HIGH", "123-45-6789"));

        var result = mockMvc.perform(get("/api/dossiers/export.csv")
                        .param("dossierTypeCode", code)
                        .with(user("admin-export-1", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"dossiers-" + code + ".csv\""))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("reference,label,status,createdAt,updatedAt,closedAt,priority,ssn");
        assertThat(body).contains("123-45-6789");
    }

    @Test
    void exportCsv_viewerWithViewAcl_masksSensitiveField() throws Exception {
        String code = createTypeWithSensitiveField("EXP_VIEWER");
        grant(code, "viewer-export-1", "VIEW");
        createDossier(code, "admin-" + code, fieldsJson("HIGH", "123-45-6789"));

        var result = mockMvc.perform(get("/api/dossiers/export.csv")
                        .param("dossierTypeCode", code)
                        .with(user("viewer-export-1", "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("123-45-6789");
        assertThat(body).contains("HIGH");
    }

    @Test
    void exportCsv_viewerWithoutAcl_returns403() throws Exception {
        String code = createTypeWithSensitiveField("EXP_DENIED");

        mockMvc.perform(get("/api/dossiers/export.csv")
                        .param("dossierTypeCode", code)
                        .with(user("viewer-export-denied", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportCsv_unknownDossierType_returns404() throws Exception {
        mockMvc.perform(get("/api/dossiers/export.csv")
                        .param("dossierTypeCode", "GHOST-" + UUID.randomUUID())
                        .with(user("admin-ghost", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportCsv_closedDossier_populatesClosedAtColumn() throws Exception {
        String code = createTypeWithSensitiveField("EXP_CLOSED");
        String dossierId = createDossierAndGetId(code, "admin-export-closed", fieldsJson("LOW", "000-00-0000"));
        transitionStatus(dossierId, "admin-export-closed", "OUVERT");
        transitionStatus(dossierId, "admin-export-closed", "CLOTURE");

        var result = mockMvc.perform(get("/api/dossiers/export.csv")
                        .param("dossierTypeCode", code)
                        .with(user("admin-export-closed", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String[] lines = body.split("\r\n");
        assertThat(lines).hasSize(2);
        String[] row = lines[1].split(",");
        assertThat(row[2]).isEqualTo("CLOTURE"); // status
        assertThat(row[5]).isNotBlank(); // closedAt must not be empty for a closed dossier
    }

    @Test
    void exportCsv_missingDossierTypeCode_returns400() throws Exception {
        mockMvc.perform(get("/api/dossiers/export.csv")
                        .with(user("admin-missing", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private String createTypeWithSensitiveField(String prefix) throws Exception {
        String code = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Label %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": true,
                                  "fieldDefinitions": [
                                    {"name": "priority", "label": "Priority", "type": "STRING", "required": false, "sensitive": false},
                                    {"name": "ssn", "label": "SSN", "type": "STRING", "required": false, "sensitive": true}
                                  ]
                                }
                                """.formatted(code, code, code.toLowerCase()))
                        .with(csrf()))
                .andExpect(status().isCreated());
        return code;
    }

    private void grant(String code, String userId, String permission) throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-" + code, "TENANT_ADMIN"))
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

    private void createDossier(String code, String userId, String fieldsJson) throws Exception {
        mockMvc.perform(post("/api/dossiers")
                        .with(user(userId, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "label": "Dossier %s",
                                  "fields": %s
                                }
                                """.formatted(code, code, fieldsJson)))
                .andExpect(status().isCreated());
    }

    private String createDossierAndGetId(String code, String userId, String fieldsJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/dossiers")
                        .with(user(userId, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "label": "Dossier %s",
                                  "fields": %s
                                }
                                """.formatted(code, code, fieldsJson)))
                .andExpect(status().isCreated())
                .andReturn();
        var matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("missing dossier id");
        }
        return matcher.group(1);
    }

    private void transitionStatus(String dossierId, String userId, String status) throws Exception {
        mockMvc.perform(patch("/api/dossiers/{id}/status", dossierId)
                        .with(user(userId, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "%s"
                                }
                                """.formatted(status)))
                .andExpect(status().isOk());
    }

    private static String fieldsJson(String priority, String ssn) {
        return """
                {"priority": "%s", "ssn": "%s"}
                """.formatted(priority, ssn);
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        String[] effective = roles.length == 0 ? new String[] {"DOSSIER_MANAGER"} : roles;
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID))
                .authorities(Arrays.stream(effective)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
