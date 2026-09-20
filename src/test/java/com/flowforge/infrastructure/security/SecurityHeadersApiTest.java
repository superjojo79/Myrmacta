package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class SecurityHeadersApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bffAuthenticated_emitsA4HeadersWithoutHsts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("tenant_id", "tenant_dev")
                                        .claim("email", "ada@example.com")
                                        .claim("name", "Ada Lovelace"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WORKFLOW_ADMIN"))))
                .andReturn();

        assertA4HeadersWithoutHsts(result.getResponse());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void webhookAnonymous_emitsA4HeadersWithoutHsts() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/webhooks/{id}", UUID.randomUUID())
                        .header("Idempotency-Key", "headers-probe-webhooks")
                        .header("X-Webhook-Token", "irrelevant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertA4HeadersWithoutHsts(result.getResponse());
    }

    @Test
    void callbackAnonymous_emitsA4HeadersWithoutHsts() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/callbacks/{id}", UUID.randomUUID())
                        .header("Idempotency-Key", "headers-probe-callbacks")
                        .header("X-Webhook-Token", "irrelevant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertA4HeadersWithoutHsts(result.getResponse());
    }

    @Test
    void m2mAuthenticated_emitsA4HeadersWithoutHsts() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/m2m/mcp")
                        .with(jwt().jwt(builder -> builder
                                        .claim("tenant_id", "tenant_dev")
                                        .claim("roles", List.of("API_SERVICE")))
                                .authorities(new SimpleGrantedAuthority("API_SERVICE")))
                        .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                                  "protocolVersion":"2025-06-18",
                                  "capabilities":{},
                                  "clientInfo":{"name":"headers-probe","version":"0.1.0"}
                                }}
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isLessThan(400);
        assertA4HeadersWithoutHsts(result.getResponse());
    }

    static void assertA4HeadersWithoutHsts(MockHttpServletResponse response) {
        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY);
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }
}
