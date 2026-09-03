package com.flowforge.infrastructure.web.connector;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.NoopEmailPortTestConfiguration;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Dual execute URLs (DD-EXECUTE-ROUTE) — BFF Order 3 + M2M Order 1.
 */
@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@Import({OidcClientTestConfiguration.class, NoopEmailPortTestConfiguration.class})
class ConnectorExecuteApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void execute_bffTenantAdmin_returns200() throws Exception {
        String id = createSignalConnector();

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signalName": "connectorSmokeSignal",
                                  "variables": {"from": "bff"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signaled").value(true))
                .andExpect(jsonPath("$.signalName").value("connectorSmokeSignal"));
    }

    @Test
    void execute_bffNonAdmin_returns403() throws Exception {
        String id = createSignalConnector();

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-viewer", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void execute_m2mApiService_returns200() throws Exception {
        String id = createSignalConnector();

        mockMvc.perform(post("/api/m2m/connectors/{id}/execute", id)
                        .with(m2mJwt(TENANT_ID, "API_SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signalName": "connectorSmokeSignal"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signaled").value(true));
    }

    @Test
    void execute_m2mWrongTenant_returns403() throws Exception {
        String id = createSignalConnector();

        mockMvc.perform(post("/api/m2m/connectors/{id}/execute", id)
                        .with(m2mJwt("other_tenant", "API_SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void execute_m2mWithoutApiService_returns403() throws Exception {
        String id = createSignalConnector();

        mockMvc.perform(post("/api/m2m/connectors/{id}/execute", id)
                        .with(jwt().jwt(builder -> builder.claim("tenant_id", TENANT_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void execute_m2mAnonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/m2m/connectors/{id}/execute", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void execute_bffHttpRestInstance_urlReachesConnector() throws Exception {
        String id = createHttpRestConnector();

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://127.0.0.1/internal"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not allowed")));
    }

    @Test
    void execute_m2mHttpRestInstance_urlReachesConnector() throws Exception {
        String id = createHttpRestConnector();

        mockMvc.perform(post("/api/m2m/connectors/{id}/execute", id)
                        .with(m2mJwt(TENANT_ID, "API_SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://127.0.0.1/internal", "method": "POST", "body": {"k": "v"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not allowed")));
    }

    @Test
    void execute_bffHttpRestInstance_missingUrl_returns400WithUrlRequiredDetail() throws Exception {
        String id = createHttpRestConnector();

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("url")));
    }

    @Test
    void execute_bffSmtpInstance_validInput_returns200AndSendsEmail() throws Exception {
        String id = createSmtpConnector();

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to": "user@example.com", "subject": "Subject", "body": "Body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));
    }

    @Test
    void execute_m2mSmtpInstance_validInput_returns200() throws Exception {
        String id = createSmtpConnector();

        mockMvc.perform(post("/api/m2m/connectors/{id}/execute", id)
                        .with(m2mJwt(TENANT_ID, "API_SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to": "user@example.com", "subject": "Subject", "body": "Body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));
    }

    @Test
    void execute_bffSmtpInstance_missingTo_returns400WithToRequiredDetail() throws Exception {
        String id = createSmtpConnector();

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject": "Subject", "body": "Body"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("to")));
    }

    @Test
    void execute_bffDocumentStorageInstance_store_returns200WithStorageRef() throws Exception {
        String id = createDocumentStorageConnector();
        String content = java.util.Base64.getEncoder()
                .encodeToString("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operation": "STORE", "content": "%s", "contentType": "text/plain"}
                                """.formatted(content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageRef").isNotEmpty());
    }

    @Test
    void execute_m2mDocumentStorageInstance_store_returns200() throws Exception {
        String id = createDocumentStorageConnector();
        String content = java.util.Base64.getEncoder()
                .encodeToString("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/m2m/connectors/{id}/execute", id)
                        .with(m2mJwt(TENANT_ID, "API_SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operation": "STORE", "content": "%s", "contentType": "text/plain"}
                                """.formatted(content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageRef").isNotEmpty());
    }

    @Test
    void execute_bffDocumentStorageInstance_missingOperation_returns400WithOperationRequiredDetail() throws Exception {
        String id = createDocumentStorageConnector();

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("operation")));
    }

    @Test
    void execute_bffDisabledConnector_returns404() throws Exception {
        String id = createSignalConnector();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/connectors/{id}", id)
                        .with(oidcUser("seed-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void execute_bffSoftDeletedConnector_returns404() throws Exception {
        String id = createSignalConnector();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/connectors/{id}", id)
                        .with(oidcUser("seed-admin", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/connectors/{id}/execute", id)
                        .with(oidcUser("exec-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private String createSignalConnector() throws Exception {
        String name = "Signal exec " + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/connectors")
                        .with(oidcUser("seed-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "connectorType": "flowable-signal",
                                  "enabled": true,
                                  "config": {
                                    "signalName": "connectorSmokeSignal",
                                    "processKey": "signalCatchSmoke",
                                    "dossierTypeCode": "CLAIM"
                                  }
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private String createHttpRestConnector() throws Exception {
        String name = "HTTP REST exec " + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/connectors")
                        .with(oidcUser("seed-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "connectorType": "http-rest",
                                  "enabled": true,
                                  "config": {
                                    "webhookAuthType": "HMAC_SHA256",
                                    "webhookSecret": "webhook-secret-0123456789",
                                    "processKey": "smokeProcess",
                                    "dossierTypeCode": "CLAIM"
                                  }
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private String createSmtpConnector() throws Exception {
        String name = "SMTP exec " + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/connectors")
                        .with(oidcUser("seed-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "connectorType": "smtp",
                                  "enabled": true,
                                  "config": {}
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private String createDocumentStorageConnector() throws Exception {
        String name = "Document storage exec " + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/connectors")
                        .with(oidcUser("seed-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "connectorType": "document-storage",
                                  "enabled": true,
                                  "config": {}
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private static RequestPostProcessor oidcUser(String subject, String... roles) {
        return oidcLogin().idToken(token -> token
                        .subject(subject)
                        .claim("tenant_id", TENANT_ID))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static RequestPostProcessor m2mJwt(String tenantId, String authority) {
        return jwt().jwt(builder -> builder
                        .claim("tenant_id", tenantId)
                        .claim("roles", List.of(authority)))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
