package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
@TestPropertySource(properties = "app.security.headers.enabled=false")
class SecurityHeadersDisabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bffAuthenticated_doesNotEmitA4Packet() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("tenant_id", "tenant_dev")
                                        .claim("email", "ada@example.com")
                                        .claim("name", "Ada Lovelace"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WORKFLOW_ADMIN"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Content-Security-Policy")).isNull();
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isNull();
    }
}
