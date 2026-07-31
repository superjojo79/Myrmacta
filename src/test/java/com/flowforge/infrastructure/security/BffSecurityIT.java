package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class BffSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void me_anonymous_returns401EmptyBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
    }

    @Test
    void me_authenticatedWithMatchingTenant_returnsProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("tenant_id", "tenant_dev")
                                        .claim("email", "ada@example.com")
                                        .claim("name", "Ada Lovelace"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_WORKFLOW_ADMIN"),
                                        new SimpleGrantedAuthority("ROLE_DOSSIER_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0]").value("DOSSIER_VIEWER"))
                .andExpect(jsonPath("$.roles[1]").value("WORKFLOW_ADMIN"));
    }

    @Test
    void me_mismatchedTenant_returns403AndInvalidatesSession() throws Exception {
        MvcResult forbidden = mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token.claim("tenant_id", "tenant_a"))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(forbidden.getResponse().getContentAsString()).isEmpty();

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void me_missingTenantClaim_returns403() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(oidcLogin()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    @Test
    void mutatingRequest_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(oidcLogin()
                                .idToken(token -> token.claim("tenant_id", "tenant_dev")))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutatingRequest_withCsrf_doesNotFailForCsrf() throws Exception {
        // Logout may redirect to IdP end_session — assert not 403 CSRF.
        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .with(oidcLogin()
                                .idToken(token -> token.claim("tenant_id", "tenant_dev")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    void clientRegistration_usesOidcRegistrationId() {
        assertThat(clientRegistrationRepository.findByRegistrationId("oidc")).isNotNull();
        assertThat(clientRegistrationRepository.findByRegistrationId("oidc").getRegistrationId())
                .isEqualTo("oidc");
    }
}
