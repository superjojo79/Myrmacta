package be.myrmacta.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class RetentionApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void getRetentionSettings_absent_returns404() throws Exception {
        jdbcTemplate.update("DELETE FROM tenant_retention_settings WHERE tenant_id = ?", TENANT_ID);

        mockMvc.perform(get("/api/tenant/retention-settings")
                        .with(user("admin-ret-absent", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRetentionSettings_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/tenant/retention-settings")
                        .with(user("mgr-ret", "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void putAndGetRetentionSettings_tenantAdmin_roundTrip() throws Exception {
        mockMvc.perform(put("/api/tenant/retention-settings")
                        .with(user("admin-ret", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRetentionDays\": 42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.defaultRetentionDays").value(42));

        mockMvc.perform(get("/api/tenant/retention-settings")
                        .with(user("admin-ret-get", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultRetentionDays").value(42));
    }

    @Test
    void putRetentionSettings_invalidDays_returns400() throws Exception {
        mockMvc.perform(put("/api/tenant/retention-settings")
                        .with(user("admin-ret-bad", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRetentionDays\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putRetentionSettings_nonAdmin_returns403() throws Exception {
        mockMvc.perform(put("/api/tenant/retention-settings")
                        .with(user("viewer-ret", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRetentionDays\": 30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createType_withRetentionNever_roundTrips() throws Exception {
        String code = "RET" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-type-ret", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Label %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": [],
                                  "retentionDays": null,
                                  "retentionNever": true
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retentionNever").value(true))
                .andExpect(jsonPath("$.retentionDays").value((Object) null));

        // D-TYPE-VIS: retention fields on GET when caller is allowed (TENANT_ADMIN)
        mockMvc.perform(get("/api/dossier-types/{code}", code)
                        .with(user("admin-type-ret", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionNever").value(true))
                .andExpect(jsonPath("$.retentionDays").doesNotExist());
    }

    @Test
    void patchType_forceInherit_clearsOverride() throws Exception {
        String code = "INH" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-inh", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Label %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": [],
                                  "retentionDays": 90,
                                  "retentionNever": false
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retentionDays").value(90));

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("admin-inh", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionNever\": false, \"retentionDays\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionNever").value(false))
                .andExpect(jsonPath("$.retentionDays").value((Object) null));
    }

    @Test
    void createDossier_exposesNullPurgeEligibleAt() throws Exception {
        String code = "PRG" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-prg", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Label %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": []
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("admin-prg", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectUserId":"mgr-prg","permission":"EDIT"}
                                """))
                .andExpect(status().isNoContent());

        MvcResult created = mockMvc.perform(post("/api/dossiers")
                        .with(user("mgr-prg"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "label": "Purge field dossier",
                                  "fields": {}
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purgeEligibleAt").value((Object) null))
                .andReturn();

        String id = created.getResponse().getContentAsString().replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/dossiers/{id}", id).with(user("mgr-prg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgeEligibleAt").value((Object) null));
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        String[] effective = roles.length == 0 ? new String[] {"DOSSIER_MANAGER"} : roles;
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", TENANT_ID)
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(effective)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
