package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * D-CALLBACK-ROUTE (found in adversarial security review, post-merge of TODO §51) : proves
 * against the real Spring Security filter chain (not {@code MockMvc.standaloneSetup}, which
 * bypasses it entirely) that both anonymous webhook-style endpoints are reachable without an
 * OIDC session/CSRF token -- {@code /api/callbacks/**} was missing from every
 * {@code securityMatcher} and fell through to the authenticated BFF catch-all chain, making it
 * return a bare 403 (CSRF) before ever reaching {@code CallbackWebhookController}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class WebhookAndCallbackRoutesSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void webhooksEndpoint_anonymousPost_reachesController_notBlockedBySecurityChain() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/webhooks/{id}", UUID.randomUUID())
                        .header("Idempotency-Key", "route-probe-webhooks")
                        .header("X-Webhook-Token", "irrelevant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        // Unknown instance -> the *controller's* 404, never a Security-layer 403/redirect.
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void callbacksEndpoint_anonymousPost_reachesController_notBlockedBySecurityChain() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/callbacks/{id}", UUID.randomUUID())
                        .header("Idempotency-Key", "route-probe-callbacks")
                        .header("X-Webhook-Token", "irrelevant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void callbacksEndpoint_oversizedDeclaredContentLength_rejectedBeforeBuffering() throws Exception {
        // D-CALLBACK-ROUTE also carried WebhookBodySizeLimitFilter along -- proves it, not just
        // the routing fix, actually applies to /api/callbacks/** end to end.
        MvcResult result = mockMvc.perform(post("/api/callbacks/{id}", UUID.randomUUID())
                        .header("Idempotency-Key", "route-probe-oversized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[2_000_000]))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(413);
    }
}
