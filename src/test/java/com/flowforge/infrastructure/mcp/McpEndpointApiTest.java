package com.flowforge.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
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
 * IT-MCP-1 : preuve que POST {@code /api/m2m/mcp} est bien l'endpoint MCP Streamable HTTP
 * configuré ({@code spring.ai.mcp.server.streamable-http.mcp-endpoint}), sous la même chaîne
 * Security M2M ({@code Order 1}, JWT {@code API_SERVICE}) que {@code /api/m2m/connectors/**}.
 * {@code initialize} + {@code tools/list} : exactement 9 tools nommés (T6.3). Vérifie aussi que
 * le SDK MCP convertit une exception de tool en {@code isError=true} (message, pas de stack
 * trace) sans mapper dédié.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class McpEndpointApiTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final List<String> EXPECTED_TOOLS = List.of(
            "define_dossier_type",
            "deploy_workflow",
            "get_dossier",
            "get_dossier_type",
            "list_document_templates",
            "list_dossier_types",
            "list_workflow_types",
            "query_sla_summary",
            "search_dossiers");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void initialize_onM2mMcpEndpoint_isHandledByMcpTransport() throws Exception {
        MvcResult result = callMcp(null, 1, "initialize", """
                {
                  "protocolVersion": "2025-06-18",
                  "capabilities": {},
                  "clientInfo": {"name": "flowforge-test", "version": "0.1.0"}
                }
                """);

        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("POST /api/m2m/mcp status: %s, body: %s", status, result.getResponse().getContentAsString())
                .isLessThan(400);
    }

    @Test
    void toolsList_registersExactlyTheNineExpectedTools() throws Exception {
        String sessionId = initializeSession();

        MvcResult list = callMcp(sessionId, 2, "tools/list", "{}");
        String body = list.getResponse().getContentAsString();

        for (String toolName : EXPECTED_TOOLS) {
            assertThat(body).as("tools/list body: %s", body).contains("\"name\":\"" + toolName + "\"");
        }
        assertThat(countOccurrences(body, "\"name\":\"")).isEqualTo(EXPECTED_TOOLS.size());
    }

    @Test
    void toolsCall_defineDossierTypeDryRun_succeedsWithoutError() throws Exception {
        String sessionId = initializeSession();

        MvcResult call = callMcp(sessionId, 3, "tools/call", """
                {
                  "name": "define_dossier_type",
                  "arguments": {
                    "code": "MCPITTEST",
                    "label": "IT Test",
                    "workflowKey": "wf-it-test",
                    "active": true,
                    "allowUnknownFields": false,
                    "fieldDefinitions": [],
                    "dryRun": true
                  }
                }
                """);

        String body = call.getResponse().getContentAsString();
        assertThat(body).contains("\"isError\":false").contains("MCPITTEST").contains("create");
    }

    @Test
    void toolsCall_forbiddenRetentionKey_returnsIsErrorTrueWithMessage() throws Exception {
        String sessionId = initializeSession();

        MvcResult call = callMcp(sessionId, 4, "tools/call", """
                {
                  "name": "define_dossier_type",
                  "arguments": {
                    "code": "MCPITTEST2",
                    "label": "IT Test",
                    "workflowKey": "wf-it-test",
                    "active": true,
                    "allowUnknownFields": false,
                    "fieldDefinitions": [],
                    "retentionDays": 30
                  }
                }
                """);

        String body = call.getResponse().getContentAsString();
        assertThat(body)
                .contains("\"isError\":true")
                .contains("retentionDays cannot be set via define_dossier_type");
    }

    private String initializeSession() throws Exception {
        MvcResult init = callMcp(null, 1, "initialize", """
                {
                  "protocolVersion": "2025-06-18",
                  "capabilities": {},
                  "clientInfo": {"name": "flowforge-test", "version": "0.1.0"}
                }
                """);
        return init.getResponse().getHeader("Mcp-Session-Id");
    }

    private MvcResult callMcp(String sessionId, int id, String method, String params) throws Exception {
        var request = post("/api/m2m/mcp")
                .with(m2mJwt(TENANT_ID, "API_SERVICE"))
                .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}".formatted(id, method, params));
        if (sessionId != null) {
            request = request.header("Mcp-Session-Id", sessionId);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static RequestPostProcessor m2mJwt(String tenantId, String authority) {
        return jwt().jwt(builder -> builder
                        .claim("tenant_id", tenantId)
                        .claim("roles", List.of(authority)))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
