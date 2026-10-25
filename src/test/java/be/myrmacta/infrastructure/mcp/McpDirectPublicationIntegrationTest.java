package be.myrmacta.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import be.myrmacta.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.UUID;
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
 * TODO §82 (D6, D-MCP-LC-PUBLISH = C) — instance explicitement configurée en publication directe (hors production) : le même
 * outil publie, par le même cas d'usage, et la publication est journalisée en mode DIRECT.
 */
@SpringBootTest(properties = {"app.mcp.publication.mode=direct", "app.environment=staging",
        "app.security.m2m.trusted-client-ids=agent-1"})
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class McpDirectPublicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase;

    private static RequestPostProcessor m2m() {
        return jwt().jwt(b -> b.claim("sub", "agent-1").claim("tenant_id", "tenant_dev").claim("roles", List.of("API_SERVICE")))
                .authorities(new SimpleGrantedAuthority("API_SERVICE"));
    }

    private MvcResult call(String session, int id, String method, String params) throws Exception {
        var req = post("/api/m2m/mcp").with(m2m())
                .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}".formatted(id, method, params));
        if (session != null) {
            req = req.header("Mcp-Session-Id", session);
        }
        return mockMvc.perform(req).andReturn();
    }

    @Test
    void deployWorkflowPublishesImmediatelyWhenTheInstanceAllowsDirectPublication() throws Exception {
        MvcResult init = call(null, 1, "initialize", """
                {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "t", "version": "0.1.0"}}
                """);
        String session = init.getResponse().getHeader("Mcp-Session-Id");
        call(session, 0, "notifications/initialized", "{}");

        String key = "mcpDirect" + UUID.randomUUID().toString().substring(0, 8);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://flowforge/test">
                  <process id="%s" isExecutable="true"><startEvent id="s"/><userTask id="w" name="W"/><endEvent id="e"/>
                    <sequenceFlow id="f1" sourceRef="s" targetRef="w"/><sequenceFlow id="f2" sourceRef="w" targetRef="e"/></process>
                </definitions>
                """.formatted(key).replace("\"", "\\\"").replace("\n", "\\n");
        MvcResult result = call(session, 2, "tools/call", """
                {"name": "deploy_workflow", "arguments": {"processDefinitionKey": "%s", "xml": "%s", "label": "Direct",
                 "statusDefinitions": [{"code": "STARTED", "label": "Started", "initial": true, "outcome": "NONE"}],
                 "allowedDossierStatuses": ["OUVERT"]}}
                """.formatted(key, xml));

        String body = result.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":false").contains("PUBLISHED").contains("DIRECT");
        assertThat(getWorkflowTypeConfigUseCase.getByKey(key)).isPresent();
    }
}
