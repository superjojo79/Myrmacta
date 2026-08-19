package com.flowforge.infrastructure.web.notification;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class NotificationWebhookAdminApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void create_returns201AndMasksSecret() throws Exception {
        String name = "Hook " + UUID.randomUUID();
        mockMvc.perform(post("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "targetUrl": "https://example.com/hook",
                                  "secret": "plain-secret",
                                  "enabled": true
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/hook"))
                .andExpect(jsonPath("$.secret").value("********"))
                .andExpect(jsonPath("$.secretConfigured").value(true));
    }

    @Test
    void softDelete_hidesFromList_restoreMakesVisible() throws Exception {
        String name = "Soft " + UUID.randomUUID();
        String id = createWebhook(name);

        mockMvc.perform(delete("/api/notification-webhooks/{id}", id)
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notification-webhooks/{id}", id)
                        .with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", not(hasItem(name))));

        mockMvc.perform(get("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));

        mockMvc.perform(patch("/api/notification-webhooks/{id}", id)
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restore\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));

        mockMvc.perform(get("/api/notification-webhooks/{id}", id)
                        .with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void patch_updatesUrlAndEnable() throws Exception {
        String id = createWebhook("Patch " + UUID.randomUUID());
        mockMvc.perform(patch("/api/notification-webhooks/{id}", id)
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUrl": "https://example.com/updated",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/updated"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.secret").value("********"));
    }

    @Test
    void create_ssrfUrl_returns400() throws Exception {
        // A13: loopback http is allowed on profile test; use metadata IP over https.
        mockMvc.perform(post("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "SSRF %s",
                                  "targetUrl": "https://169.254.169.254/latest/meta-data",
                                  "enabled": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_httpLocalhost_allowedOnTestProfile() throws Exception {
        String name = "Local " + UUID.randomUUID();
        mockMvc.perform(post("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "targetUrl": "http://localhost:8089/hook",
                                  "enabled": true
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetUrl").value("http://localhost:8089/hook"));
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        String name = "Dup " + UUID.randomUUID();
        createWebhook(name);
        mockMvc.perform(post("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "targetUrl": "https://example.com/hook2",
                                  "enabled": true
                                }
                                """.formatted(name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void nonTenantAdmin_returns403_anonymous401_csrf403() throws Exception {
        mockMvc.perform(get("/api/notification-webhooks")
                        .with(user("viewer", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/notification-webhooks")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "No CSRF %s",
                                  "targetUrl": "https://example.com/hook",
                                  "enabled": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private String createWebhook(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notification-webhooks")
                        .with(user("admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "targetUrl": "https://example.com/hook",
                                  "secret": "plain-secret",
                                  "enabled": true
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonMapper.builder()
                .build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        return oidcLogin().idToken(token -> token
                        .subject(subject)
                        .claim("tenant_id", TENANT_ID))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
