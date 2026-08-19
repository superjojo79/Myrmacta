package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
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
class PurgeOutboxApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcessPurgeOutboxUseCase processPurgeOutboxUseCase;

    @Test
    void getPurgeOutbox_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/purge-outbox").with(user("mgr-purge", "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPurgeOutbox_tenantAdmin_returnsCountsAndItems() throws Exception {
        mockMvc.perform(get("/api/purge-outbox").with(user("admin-purge", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.pending").exists())
                .andExpect(jsonPath("$.counts.processing").exists())
                .andExpect(jsonPath("$.counts.failed").exists())
                .andExpect(jsonPath("$.counts.completed").exists())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void enqueuePurge_openDossier_returns409() throws Exception {
        String dossierId = createOpenDossier();
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", dossierId)
                        .with(user("admin-enq-open", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void enqueuePurge_unknownDossier_returns404() throws Exception {
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", UUID.randomUUID())
                        .with(user("admin-enq-404", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void enqueuePurge_cloture_returns201ThenIdempotent200() throws Exception {
        String dossierId = createClotureDossier();
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", dossierId)
                        .with(user("admin-enq-ok", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", dossierId)
                        .with(user("admin-enq-ok", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/purge-outbox")
                        .param("status", "PENDING")
                        .with(user("admin-enq-ok", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].dossierId").value(org.hamcrest.Matchers.hasItem(dossierId)))
                .andExpect(jsonPath("$.items[*].origin").value(org.hamcrest.Matchers.hasItem("ADMIN")));
    }

    @Test
    void enqueuePurge_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", UUID.randomUUID())
                        .with(user("mgr-enq", "DOSSIER_MANAGER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void enqueuePurge_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", UUID.randomUUID())
                        .with(user("admin-csrf", "TENANT_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void enqueuePurge_archive_returns201() throws Exception {
        String dossierId = createArchiveDossier();
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", dossierId)
                        .with(user("admin-enq-arch", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/purge-outbox")
                        .param("status", "PENDING")
                        .with(user("admin-enq-arch", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].dossierId").value(org.hamcrest.Matchers.hasItem(dossierId)))
                .andExpect(jsonPath("$.items[*].origin").value(org.hamcrest.Matchers.hasItem("ADMIN")));
    }

    @Test
    void enqueueThenProcess_getDossierReturns404() throws Exception {
        String dossierId = createClotureDossier();
        mockMvc.perform(post("/api/dossiers/{id}/purge-requests", dossierId)
                        .with(user("admin-purge-run", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        int processed = processPurgeOutboxUseCase.processBatch(Instant.now(), 2000);
        assertThat(processed).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/dossiers/{id}", dossierId).with(user("admin-purge-run", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private String createOpenDossier() throws Exception {
        String code = "PO" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        createTypeAndAcl(code, "mgr-open-purge");
        MvcResult created = mockMvc.perform(post("/api/dossiers")
                        .with(user("mgr-open-purge"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dossierTypeCode":"%s","label":"Open purge","fields":{}}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn();
        return idFrom(created);
    }

    private String createClotureDossier() throws Exception {
        String code = "PC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        createTypeAndAcl(code, "mgr-cloture-purge");
        MvcResult created = mockMvc.perform(post("/api/dossiers")
                        .with(user("mgr-cloture-purge"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dossierTypeCode":"%s","label":"Closed purge","fields":{}}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = idFrom(created);
        jdbcTemplate.update("UPDATE dossier SET status = 'CLOTURE', closed_at = now(), closed_by = ? WHERE id = ?",
                "mgr-cloture-purge",
                UUID.fromString(id));
        return id;
    }

    private String createArchiveDossier() throws Exception {
        String code = "PA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        createTypeAndAcl(code, "mgr-archive-purge");
        MvcResult created = mockMvc.perform(post("/api/dossiers")
                        .with(user("mgr-archive-purge"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dossierTypeCode":"%s","label":"Archived purge","fields":{}}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = idFrom(created);
        jdbcTemplate.update(
                """
                UPDATE dossier
                   SET status = 'ARCHIVE', closed_at = now(), closed_by = ?, purge_eligible_at = now()
                 WHERE id = ?
                """,
                "mgr-archive-purge",
                UUID.fromString(id));
        return id;
    }

    private void createTypeAndAcl(String code, String managerId) throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-purge-type", "TENANT_ADMIN"))
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
                        .with(user("admin-purge-type", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\":\"%s\",\"permission\":\"EDIT\"}".formatted(managerId)))
                .andExpect(status().isNoContent());
    }

    private static String idFrom(MvcResult created) throws Exception {
        return created.getResponse().getContentAsString().replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
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
