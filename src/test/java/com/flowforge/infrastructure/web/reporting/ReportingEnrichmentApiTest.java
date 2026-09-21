package com.flowforge.infrastructure.web.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.infrastructure.reporting.BiExportJob;
import com.flowforge.infrastructure.reporting.SlaAlertJob;
import com.flowforge.support.OidcClientTestConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ReportingEnrichmentApiTest {

    private static final String TENANT = "tenant_dev";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SlaAlertJob slaAlertJob;

    @Autowired
    private BiExportJob biExportJob;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM tenant_sla_alert_webhook WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM sla_alert_state WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM bi_export_file WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM notification_outbox WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM tenant_sla_settings WHERE tenant_id = ?", TENANT);
    }

    @Test
    void settings_roundTripAndRejectsNonAdmin() throws Exception {
        mockMvc.perform(put("/api/tenant/sla-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "complianceTargetPercent": 90,
                                  "alertingEnabled": false,
                                  "alertIntervalMinutes": 15,
                                  "alertEmails": ["ops@example.com"],
                                  "webhookIds": [],
                                  "biExportEnabled": true,
                                  "biIntervalHours": 2,
                                  "biRetentionDays": 7
                                }
                                """)
                        .with(csrf())
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertIntervalMinutes").value(15))
                .andExpect(jsonPath("$.alertEmails[0]").value("ops@example.com"))
                .andExpect(jsonPath("$.biIntervalHours").value(2))
                .andExpect(jsonPath("$.biRetentionDays").value(7))
                .andExpect(jsonPath("$.biEnabledAt").isNotEmpty())
                .andExpect(jsonPath("$.lastBiExportAt").doesNotExist());

        mockMvc.perform(get("/api/tenant/sla-settings").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biExportEnabled").value(true));

        mockMvc.perform(put("/api/tenant/sla-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complianceTargetPercent\":90,\"alertEmails\":[\"nope\"]}")
                        .with(csrf())
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/tenant/sla-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complianceTargetPercent\":90}")
                        .with(csrf())
                        .with(user("manager", "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void operations_visibleToReportRolesOnly() throws Exception {
        mockMvc.perform(get("/api/reports/operations").with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLetterJobCount").isNumber())
                .andExpect(jsonPath("$.unassignedOpenTaskCount").isNumber());
        mockMvc.perform(get("/api/reports/operations").with(user("manager", "DOSSIER_MANAGER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/operations").with(user("viewer", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void alerting_snapshotDoesNotSend_laterCrossingSendsOnceOnThreeChannels() throws Exception {
        String code = "ALR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Alerte %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": [],
                                  "slaTargetHours": 24
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated());
        MvcResult created = mockMvc.perform(post("/api/dossiers")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dossierTypeCode": "%s", "label": "Late", "fields": {}}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode dossier = JSON.readTree(created.getResponse().getContentAsString());
        UUID dossierId = UUID.fromString(dossier.get("id").asString());
        String reference = dossier.get("reference").asString();
        jdbcTemplate.update(
                "UPDATE dossier SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")),
                dossierId);
        jdbcTemplate.update(
                """
                INSERT INTO dossier_workflow_link
                  (id, dossier_id, tenant_id, process_definition_key, sequence, status, trigger_ref, started_at, started_by)
                VALUES (?, ?, ?, ?, 1, 'ACTIVE', 'manual', ?, ?)
                """,
                UUID.randomUUID(),
                dossierId,
                TENANT,
                "wf-" + code.toLowerCase(),
                Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")),
                "starter-1");

        String webhookName = "hook-" + code;
        MvcResult hook = mockMvc.perform(post("/api/notification-webhooks")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "targetUrl": "https://example.com/hook",
                                  "secret": "plain-secret",
                                  "enabled": true
                                }
                                """.formatted(webhookName)))
                .andExpect(status().isCreated())
                .andReturn();
        String webhookId = JSON.readTree(hook.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(put("/api/tenant/sla-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "complianceTargetPercent": 90,
                                  "alertingEnabled": true,
                                  "alertIntervalMinutes": 15,
                                  "alertEmails": ["ops@example.com"],
                                  "webhookIds": ["%s"]
                                }
                                """.formatted(webhookId))
                        .with(csrf())
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastAlertScanAt").doesNotExist());

        String status = jdbcTemplate.queryForObject(
                "SELECT last_status FROM sla_alert_state WHERE tenant_id = ? AND dossier_id = ?",
                String.class,
                TENANT,
                dossierId);
        assertThat(status).isEqualTo("BREACHED");
        assertThat(channelsFor(reference)).isEmpty();

        Instant firstScan = Instant.parse("2026-09-21T12:00:00Z");
        slaAlertJob.process(firstScan);
        assertThat(channelsFor(reference)).isEmpty();

        Instant secondScan = firstScan.plus(20, ChronoUnit.MINUTES);
        jdbcTemplate.update(
                "UPDATE dossier SET created_at = ? WHERE id = ?",
                Timestamp.from(secondScan.minus(20, ChronoUnit.HOURS)),
                dossierId);
        slaAlertJob.process(secondScan);

        assertThat(channelsFor(reference)).containsExactlyInAnyOrder("EMAIL", "WEBHOOK", "BROWSER");

        slaAlertJob.process(secondScan.plus(20, ChronoUnit.MINUTES));
        assertThat(channelsFor(reference)).hasSize(3);
    }

    @Test
    void biExport_waitsForInterval_stripsSensitiveKeys_andHidesForbiddenDownload() throws Exception {
        String code = "BIX" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "BI %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": [
                                    {"name": "city", "label": "Ville", "type": "STRING", "required": false, "sensitive": false},
                                    {"name": "iban", "label": "IBAN", "type": "STRING", "required": false, "sensitive": true}
                                  ],
                                  "slaTargetHours": 48
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossiers")
                        .with(user("sla-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dossierTypeCode": "%s", "label": "=cmd", "fields": {"city": "Lyon", "iban": "FR-SECRET-99"}}
                                """.formatted(code)))
                .andExpect(status().isCreated());

        MvcResult saved = mockMvc.perform(put("/api/tenant/sla-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "complianceTargetPercent": 90,
                                  "biExportEnabled": true,
                                  "biIntervalHours": 1,
                                  "biRetentionDays": 30
                                }
                                """)
                        .with(csrf())
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        Instant enabledAt = Instant.parse(JSON.readTree(saved.getResponse().getContentAsString())
                .get("biEnabledAt")
                .asString());

        biExportJob.process(enabledAt);
        Integer none = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bi_export_file WHERE tenant_id = ?", Integer.class, TENANT);
        assertThat(none).isZero();

        // timestamptz arrondit à la microseconde : l'instant JSON peut précéder
        // l'ancre stockée, et +1 h pile n'est pas encore dû.
        biExportJob.process(enabledAt.plus(1, ChronoUnit.HOURS).plusSeconds(1));
        Integer createdFiles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bi_export_file WHERE tenant_id = ?", Integer.class, TENANT);
        assertThat(createdFiles).isGreaterThan(0);
        StringBuilder listed = new StringBuilder();
        boolean foundType = false;
        for (int page = 0; page < 20 && !foundType; page++) {
            String listBody = mockMvc.perform(get("/api/reports/bi-exports")
                            .param("page", Integer.toString(page))
                            .param("size", "100")
                            .with(user("sla-admin", "TENANT_ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            listed.append(listBody);
            foundType = listBody.contains(code);
            int totalPages = JSON.readTree(listBody).get("totalPages").asInt();
            if (page + 1 >= totalPages) {
                break;
            }
        }
        assertThat(foundType).as(listed.toString()).isTrue();

        String dossierFileId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM bi_export_file
                WHERE tenant_id = ? AND kind = 'DOSSIERS' AND dossier_type_code = ?
                """,
                String.class,
                TENANT,
                code);
        String slaFileId = jdbcTemplate.queryForObject(
                "SELECT id FROM bi_export_file WHERE tenant_id = ? AND kind = 'SLA'",
                String.class,
                TENANT);

        String csv = mockMvc.perform(get("/api/reports/bi-exports/{id}", dossierFileId)
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("dossiers-" + code)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(csv).contains("Lyon").contains("'=cmd").doesNotContain("FR-SECRET-99");

        String manual = mockMvc.perform(get("/api/dossiers/export.csv").param("dossierTypeCode", code)
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(manual).contains("FR-SECRET-99");

        mockMvc.perform(get("/api/reports/bi-exports/{id}", dossierFileId)
                        .with(user("manager", "DOSSIER_MANAGER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/bi-exports/{id}", slaFileId).with(user("manager", "DOSSIER_MANAGER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/bi-exports").with(user("manager", "DOSSIER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].dossierTypeCode", Matchers.not(Matchers.hasItem(code))));

        jdbcTemplate.update(
                "UPDATE bi_export_file SET created_at = ? WHERE tenant_id = ?",
                Timestamp.from(enabledAt.minus(10, ChronoUnit.DAYS)),
                TENANT);
        mockMvc.perform(put("/api/tenant/sla-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"complianceTargetPercent": 90, "biExportEnabled": false, "biRetentionDays": 1}
                                """)
                        .with(csrf())
                        .with(user("sla-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk());
        biExportJob.process(enabledAt.plus(2, ChronoUnit.HOURS));
        Integer left = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bi_export_file WHERE tenant_id = ?", Integer.class, TENANT);
        assertThat(left).isZero();
    }

    private java.util.List<String> channelsFor(String reference) {
        return jdbcTemplate.query(
                "SELECT channel FROM notification_outbox WHERE tenant_id = ? AND payload_json LIKE ?",
                (rs, row) -> rs.getString(1),
                TENANT,
                "%" + reference + "%");
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", TENANT)
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
