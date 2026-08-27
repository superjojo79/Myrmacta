package com.flowforge.infrastructure.mcp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.List;
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

/**
 * IT-MCP-1 (amorce, T4.3) : preuve que POST {@code /api/m2m/mcp} est bien l'endpoint MCP
 * Streamable HTTP configuré ({@code spring.ai.mcp.server.streamable-http.mcp-endpoint}), sous
 * la même chaîne Security M2M ({@code Order 1}, JWT {@code API_SERVICE}) que
 * {@code /api/m2m/connectors/**}. Si le starter n'écoutait que {@code /mcp} (A-M2M-PATH), cet
 * appel échouerait (404/405) au lieu de traiter la requête {@code initialize}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class McpEndpointApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void initialize_onM2mMcpEndpoint_isHandledByMcpTransport() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/m2m/mcp")
                        .with(m2mJwt(TENANT_ID, "API_SERVICE"))
                        .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 1,
                                  "method": "initialize",
                                  "params": {
                                    "protocolVersion": "2025-06-18",
                                    "capabilities": {},
                                    "clientInfo": {"name": "flowforge-test", "version": "0.1.0"}
                                  }
                                }
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
                .as("POST /api/m2m/mcp status: %s, body: %s", status, result.getResponse().getContentAsString())
                .isLessThan(400);
    }

    private static RequestPostProcessor m2mJwt(String tenantId, String authority) {
        return jwt().jwt(builder -> builder
                        .claim("tenant_id", tenantId)
                        .claim("roles", List.of(authority)))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
