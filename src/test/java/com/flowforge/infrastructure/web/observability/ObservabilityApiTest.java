package com.flowforge.infrastructure.web.observability;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ObservabilityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tenantAdmin_receivesSnapshotWithThreeAgesAndSeverity() throws Exception {
        mockMvc.perform(get("/api/observability").with(user("obs-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant_dev"))
                .andExpect(jsonPath("$.triggerOutboxOldestPendingAgeSeconds").isNumber())
                .andExpect(jsonPath("$.notificationOutboxOldestPendingAgeSeconds").isNumber())
                .andExpect(jsonPath("$.purgeOutboxOldestUnfinishedAgeSeconds").isNumber())
                .andExpect(jsonPath("$.flowableDeadLetterJobCount").isNumber())
                .andExpect(jsonPath("$.flowableExceptionJobCount").isNumber())
                .andExpect(jsonPath("$.pdfRender.count").isNumber())
                .andExpect(jsonPath("$.webhookDeadCount").isNumber())
                .andExpect(jsonPath("$.sseActiveCount").isNumber())
                .andExpect(jsonPath("$.severity.triggerOutboxOldestPendingAge").exists())
                .andExpect(jsonPath("$.severity.pdfRender").value("ok"));
    }

    @Test
    void otherRoles_areForbidden() throws Exception {
        mockMvc.perform(get("/api/observability").with(user("obs-mgr", "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/observability").with(user("obs-wf", "WORKFLOW_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/observability").with(user("obs-view", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
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
