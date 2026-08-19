package com.flowforge.infrastructure.web.openapi;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
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

@SpringBootTest(properties = {"app.openapi.enabled=true", "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class OpenApiEnabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanReadDocs_managerIsForbidden_hiddenOperationsAbsent() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("mgr", "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v3/api-docs").with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/dossier-types']").exists())
                .andExpect(jsonPath("$.paths['/api/document-templates']").exists())
                .andExpect(jsonPath("$.paths['/api/connectors']").exists())
                .andExpect(jsonPath("$.paths['/api/workflow-type-configs']").exists())
                .andExpect(jsonPath("$.paths['/api/notification-webhooks']").exists())
                .andExpect(jsonPath("$.paths['/api/purge-outbox']").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/stream']").doesNotExist())
                .andExpect(jsonPath("$.paths", not(hasKey("/api/webhooks/{id}"))))
                .andExpect(jsonPath("$.paths", not(hasKey("/api/dossiers/{id}/elements/attachment"))));

        mockMvc.perform(get("/v3/api-docs.yaml").with(user("wf", "WORKFLOW_ADMIN")))
                .andExpect(status().isOk());
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
