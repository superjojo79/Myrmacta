package com.flowforge.infrastructure.web.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class NotificationSendApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void send_email_returns202WithOutboxIds() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .with(user("send-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channels": ["EMAIL"],
                                  "to": "a@example.com",
                                  "subject": "Hello",
                                  "body": "World"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outboxIds", hasSize(1)));
    }

    @Test
    void send_workflowAdmin_returns202() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .with(user("wf-admin", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channels": ["EMAIL"],
                                  "to": "a@example.com",
                                  "subject": "Hi",
                                  "body": "Body"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outboxIds", hasSize(1)));
    }

    @Test
    void send_dossierManager_returns403() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .with(user("mgr", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channels": ["EMAIL"],
                                  "to": "a@example.com",
                                  "subject": "Hi",
                                  "body": "Body"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channels": ["EMAIL"],
                                  "to": "a@example.com",
                                  "subject": "Hi",
                                  "body": "Body"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void send_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .with(user("send-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channels": ["EMAIL"],
                                  "to": "a@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void send_missingCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .with(user("send-admin", "TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channels": ["EMAIL"],
                                  "to": "a@example.com",
                                  "subject": "Hi",
                                  "body": "Body"
                                }
                                """))
                .andExpect(status().isForbidden());
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
