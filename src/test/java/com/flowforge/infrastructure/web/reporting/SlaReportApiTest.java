package com.flowforge.infrastructure.web.reporting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class SlaReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tenantAdmin_receivesSnapshot() throws Exception {
        mockMvc.perform(get("/api/reports/sla").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceTargetPercent").value(95))
                .andExpect(jsonPath("$.overdueCount").isNumber())
                .andExpect(jsonPath("$.typeShares").isArray())
                .andExpect(jsonPath("$.types").isArray());
    }

    @Test
    void viewer_isForbidden() throws Exception {
        mockMvc.perform(get("/api/reports/sla").with(user("sla-view", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invertedPeriod_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/sla")
                        .param("from", "2026-03-10T00:00:00Z")
                        .param("to", "2026-03-01T00:00:00Z")
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void csv_isDownloadable() throws Exception {
        mockMvc.perform(get("/api/reports/sla.csv").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("sla-report.csv")));
    }

    @Test
    void csv_neutralizesFormulaInTypeLabel() throws Exception {
        String code = "CMD" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "=CMD",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": [],
                                  "slaTargetHours": 48
                                }
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossiers")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "label": "Formula dossier",
                                  "fields": {}
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/sla.csv").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("'=CMD")));
    }

    @Test
    void settings_roundTrip() throws Exception {
        jdbcTemplate.update("DELETE FROM tenant_sla_settings WHERE tenant_id = ?", "tenant_dev");
        mockMvc.perform(get("/api/tenant/sla-settings").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tenant/sla-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complianceTargetPercent\":90}")
                        .with(csrf())
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceTargetPercent").value(90));
        mockMvc.perform(get("/api/tenant/sla-settings").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceTargetPercent").value(90));
    }

    @Test
    void patchType_slaTargetHours_roundTripAndKeepOnOmitted() throws Exception {
        String code = "SLA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("sla-admin", "TENANT_ADMIN"))
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
                                  "slaTargetHours": 48
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slaTargetHours").value(48));

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slaTargetHours\":72}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaTargetHours").value(72));

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\": \"Kept SLA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Kept SLA"))
                .andExpect(jsonPath("$.slaTargetHours").value(72));
    }

    @Test
    void report_includesCreatedDossierInCohort() throws Exception {
        String code = "COH" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Cohorte %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": [],
                                  "slaTargetHours": 24
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossiers")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "label": "Cohort dossier",
                                  "fields": {}
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/sla")
                        .param("dossierTypeCode", code)
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types[0].typeCode").value(code))
                .andExpect(jsonPath("$.types[0].volume").value(1));
    }

    @Test
    void summary_returnsBucketsGroupedByType() throws Exception {
        mockMvc.perform(get("/api/reports/sla/summary").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("dossierTypeCode"))
                .andExpect(jsonPath("$.buckets").isArray())
                .andExpect(jsonPath("$.from").isNotEmpty())
                .andExpect(jsonPath("$.to").isNotEmpty());
    }

    @Test
    void summary_rejectsCalendarGroupBy() throws Exception {
        mockMvc.perform(get("/api/reports/sla/summary")
                        .param("groupBy", "week")
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void snapshot_acceptsCivilDateBounds() throws Exception {
        mockMvc.perform(get("/api/reports/sla")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-15")
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-03-01T00:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-03-16T00:00:00Z"));
    }

    @Test
    void patchType_clearSlaTargetHours_setsNull() throws Exception {
        String code = "CLR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("sla-admin", "TENANT_ADMIN"))
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
                                  "slaTargetHours": 48
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearSlaTargetHours\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaTargetHours").value(Matchers.nullValue()));

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearSlaTargetHours\": true, \"slaTargetHours\": 24}"))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", "tenant_dev")
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
