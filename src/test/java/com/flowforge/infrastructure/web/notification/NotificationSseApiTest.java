package com.flowforge.infrastructure.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.infrastructure.notification.SseNotificationAdapter;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Map;
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

@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class NotificationSseApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseNotificationAdapter sseNotificationAdapter;

    @Test
    void stream_authenticated_registersEmitter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications/stream")
                        .with(user("user-1", "DOSSIER_VIEWER"))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getRequest().getAsyncContext()).isNotNull();
        assertThat(sseNotificationAdapter.push("user-1", Map.of("id", "n1", "payload", Map.of())))
                .isTrue();
    }

    @Test
    void stream_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
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
