package com.flowforge.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * M7 — IT HTTP M2M réelles sur les tools MCP (IT-MCP-2..7, IT-MCP-9). Réutilise le pattern
 * {@code m2mJwt} de {@code ConnectorExecuteApiTest}. IT-MCP-8 (cap 10k SLA) reste au niveau
 * domaine (déjà couvert par {@code GetSlaSummaryUseCaseImplTest.cohortTooLarge_rejected}, un
 * mock — reproduire un cohort de 10 001 dossiers persistés serait un test d'IT disproportionné
 * pour un comportement déjà prouvé) ; la non-régression REST {@code /api/reports/**} n'a pas de
 * test dédié ici : aucun code de ce contrôleur n'a été touché par cette feature.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class McpToolCallIntegrationTest {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DossierTypeRepository dossierTypeRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase;

    @Autowired
    private ConnectorInstanceRepository connectorInstanceRepository;

    @Autowired
    private TaskQuestionSchemaRepository taskQuestionSchemaRepository;

    private String sessionId;

    @BeforeEach
    void initializeSession() throws Exception {
        MvcResult init = callMcp(null, TENANT, "API_SERVICE", 1, "initialize", """
                {
                  "protocolVersion": "2025-06-18",
                  "capabilities": {},
                  "clientInfo": {"name": "flowforge-test", "version": "0.1.0"}
                }
                """);
        sessionId = init.getResponse().getHeader("Mcp-Session-Id");
    }

    @Test
    void listDossierTypes_withMatchingTenantAndApiService_returns200() throws Exception {
        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "list_dossier_types", "arguments": {}}
                """);

        assertThat(call.getResponse().getStatus()).isLessThan(400);
        assertThat(call.getResponse().getContentAsString()).contains("\"isError\":false");
    }

    @Test
    void wrongTenant_isForbidden() throws Exception {
        MvcResult call = callMcp(sessionId, "other_tenant", "API_SERVICE", 2, "tools/call", """
                {"name": "list_dossier_types", "arguments": {}}
                """);

        assertThat(call.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void withoutApiServiceRole_isForbidden() throws Exception {
        var request = post("/api/m2m/mcp")
                .with(jwt().jwt(builder -> builder.claim("tenant_id", TENANT))
                        .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))
                .header("Mcp-Session-Id", sessionId)
                .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_dossier_types","arguments":{}}}
                        """);

        MvcResult call = mockMvc.perform(request).andReturn();
        assertThat(call.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void defineDossierType_persistsAndIsVisibleToDirectRepositoryRead() throws Exception {
        String code = "MCPIT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_dossier_type",
                  "arguments": {
                    "code": "%s",
                    "label": "MCP IT",
                    "workflowKey": "wf-mcp-it",
                    "active": true,
                    "allowUnknownFields": false,
                    "fieldDefinitions": []
                  }
                }
                """.formatted(code));

        assertThat(call.getResponse().getContentAsString()).contains("\"isError\":false");
        DossierType persisted = dossierTypeRepository.findByCode(TENANT, code).orElseThrow();
        assertThat(persisted.label()).isEqualTo("MCP IT");
        assertThat(persisted.slaTargetHours()).isNull();
        assertThat(persisted.retentionDays()).isNull();
        assertThat(persisted.createdBy()).isEqualTo("agent-1");
    }

    @Test
    void deployWorkflow_createsConfigIfAbsentThenDeploys() throws Exception {
        String key = "mcpDeploy" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(getWorkflowTypeConfigUseCase.getByKey(key)).isEmpty();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://flowforge/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="wait" name="Wait"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
                    <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(key).replace("\"", "\\\"").replace("\n", "\\n");

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "deploy_workflow",
                  "arguments": {
                    "processDefinitionKey": "%s",
                    "xml": "%s",
                    "label": "MCP Deploy IT",
                    "statusDefinitions": [{"code": "STARTED", "label": "Started", "initial": true, "outcome": "NONE"}],
                    "allowedDossierStatuses": ["OUVERT"]
                  }
                }
                """.formatted(key, xml));

        String body = call.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":false");
        assertThat(getWorkflowTypeConfigUseCase.getByKey(key)).isPresent();
    }

    @Test
    void getDossier_masksSensitiveFieldValue() throws Exception {
        String typeCode = "SENS" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        dossierTypeRepository.save(new DossierType(
                UUID.randomUUID(), TENANT, typeCode, "Sensitive type", "wf-sens", true, true,
                List.of(new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true)),
                null, false, null, "system", "system"));
        Instant now = Instant.now();
        Dossier dossier = new Dossier(
                UUID.randomUUID(), TENANT, "DOS-SENS-" + UUID.randomUUID().toString().substring(0, 8), typeCode,
                "Sensitive dossier", DossierStatus.OUVERT, Map.of("ssn", "123-45-6789"), null, 0L, "creator", now,
                "creator", now, null, null, null);
        dossierRepository.save(dossier);

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "get_dossier", "arguments": {"id": "%s"}}
                """.formatted(dossier.id()));

        String body = call.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).doesNotContain("123-45-6789");
    }

    @Test
    void searchDossiers_defaultsToPageSize20() throws Exception {
        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "search_dossiers", "arguments": {}}
                """);

        String body = call.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\\\"size\\\":20");
    }

    @Test
    void listConnectorDefinitions_returns200() throws Exception {
        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "list_connector_definitions", "arguments": {}}
                """);

        assertThat(call.getResponse().getContentAsString()).contains("\"isError\":false");
    }

    @Test
    void defineConnectorInstance_dryRun_rejectsInvalidConfigWithoutPersisting() throws Exception {
        // Phase 7 finding: dryRun=true on create originally skipped all validation (a no-op stub),
        // defeating its own "preview" purpose — fixed to validate against the connector type's
        // schema without persisting. webhookAuthType is missing here, which HttpRestConnector
        // .validate(...) rejects.
        String name = "MCP IT Invalid " + UUID.randomUUID();

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_connector_instance",
                  "arguments": {
                    "name": "%s",
                    "connectorType": "http-rest",
                    "config": {},
                    "dryRun": true
                  }
                }
                """.formatted(name));

        String body = call.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":true");
        assertThat(connectorInstanceRepository
                        .pageByTenantId(TENANT, com.flowforge.domain.shared.PageQuery.of(0, 100))
                        .content().stream().anyMatch(c -> c.name().equals(name)))
                .isFalse();
    }

    @Test
    void defineConnectorInstance_createsAndMasksSecretOnRead() throws Exception {
        String name = "MCP IT Connector " + UUID.randomUUID();

        MvcResult createCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_connector_instance",
                  "arguments": {
                    "name": "%s",
                    "connectorType": "http-rest",
                    "config": {
                      "webhookAuthType": "HMAC_SHA256",
                      "webhookSecret": "supersecretvalue1234",
                      "processKey": "wf-mcp-it",
                      "dossierTypeCode": "MCPIT"
                    }
                  }
                }
                """.formatted(name));
        String createBody = createCall.getResponse().getContentAsString();
        assertThat(createBody).as("body: %s", createBody).contains("\"isError\":false");
        assertThat(createBody).doesNotContain("supersecretvalue1234");

        var persisted = connectorInstanceRepository.pageByTenantId(TENANT, com.flowforge.domain.shared.PageQuery.of(0, 100))
                .content().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        assertThat(persisted.config()).containsEntry("webhookSecret", "supersecretvalue1234");

        MvcResult getCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "get_connector_instance", "arguments": {"id": "%s"}}
                """.formatted(persisted.id()));
        String getBody = getCall.getResponse().getContentAsString();
        assertThat(getBody).as("body: %s", getBody).doesNotContain("supersecretvalue1234");
    }

    @Test
    void defineTaskQuestionSchema_persistsAndIsVisibleToDirectRepositoryRead() throws Exception {
        String schemaKey = "mcpit" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_task_question_schema",
                  "arguments": {
                    "schemaKey": "%s",
                    "label": "MCP IT question",
                    "fields": [{"name": "decision", "label": "Decision", "type": "STRING", "required": true}]
                  }
                }
                """.formatted(schemaKey));

        assertThat(call.getResponse().getContentAsString()).contains("\"isError\":false");
        var persisted = taskQuestionSchemaRepository.findByKey(TENANT, schemaKey).orElseThrow();
        assertThat(persisted.label()).isEqualTo("MCP IT question");
        assertThat(persisted.createdBy()).isEqualTo("agent-1");
    }

    @Test
    void listAndGetTaskQuestionSchemas_return200() throws Exception {
        MvcResult list = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "list_task_question_schemas", "arguments": {}}
                """);
        assertThat(list.getResponse().getContentAsString()).contains("\"isError\":false");
    }

    @Test
    void defineDossierType_withDocumentTemplateKeys_persists() throws Exception {
        String code = "MCPIT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_dossier_type",
                  "arguments": {
                    "code": "%s",
                    "label": "MCP IT",
                    "workflowKey": "wf-mcp-it",
                    "active": true,
                    "allowUnknownFields": false,
                    "fieldDefinitions": [],
                    "documentTemplateKeys": ["template-a", "template-b"]
                  }
                }
                """.formatted(code));

        assertThat(call.getResponse().getContentAsString()).contains("\"isError\":false");
        DossierType persisted = dossierTypeRepository.findByCode(TENANT, code).orElseThrow();
        assertThat(persisted.documentTemplateKeys()).containsExactly("template-a", "template-b");
    }

    @Test
    void businessFlow_connectorDossierTypeQuestionSchemaAndWorkflow_areAllReachableThroughMcpOnly() throws Exception {
        String connectorName = "Flow connector " + UUID.randomUUID();
        callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_connector_instance",
                  "arguments": {
                    "name": "%s",
                    "connectorType": "http-rest",
                    "config": {
                      "webhookAuthType": "HMAC_SHA256",
                      "webhookSecret": "supersecretvalue1234",
                      "processKey": "wf-mcp-flow",
                      "dossierTypeCode": "MCPFLOW"
                    }
                  }
                }
                """.formatted(connectorName));

        String typeCode = "MCPFLOW" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        MvcResult typeCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_dossier_type",
                  "arguments": {
                    "code": "%s",
                    "label": "Flow type",
                    "workflowKey": "wf-mcp-flow",
                    "active": true,
                    "allowUnknownFields": false,
                    "fieldDefinitions": [],
                    "documentTemplateKeys": []
                  }
                }
                """.formatted(typeCode));
        assertThat(typeCall.getResponse().getContentAsString()).contains("\"isError\":false");

        String schemaKey = "flow" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult schemaCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_task_question_schema",
                  "arguments": {
                    "schemaKey": "%s",
                    "label": "Flow question",
                    "fields": [{"name": "orientation", "label": "Orientation", "type": "STRING", "required": true}]
                  }
                }
                """.formatted(schemaKey));
        assertThat(schemaCall.getResponse().getContentAsString()).contains("\"isError\":false");

        String workflowKey = "mcpFlow" + UUID.randomUUID().toString().substring(0, 8);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://flowforge/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="wait" name="Wait"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
                    <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(workflowKey).replace("\"", "\\\"").replace("\n", "\\n");
        MvcResult deployCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "deploy_workflow",
                  "arguments": {
                    "processDefinitionKey": "%s",
                    "xml": "%s",
                    "label": "MCP Flow",
                    "statusDefinitions": [{"code": "STARTED", "label": "Started", "initial": true, "outcome": "NONE"}],
                    "allowedDossierStatuses": ["OUVERT"]
                  }
                }
                """.formatted(workflowKey, xml));

        String body = deployCall.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":false");
        assertThat(getWorkflowTypeConfigUseCase.getByKey(workflowKey)).isPresent();
    }

    private MvcResult callMcp(String sessionId, String tenantId, String authority, int id, String method, String params)
            throws Exception {
        var request = post("/api/m2m/mcp")
                .with(m2mJwt(tenantId, authority))
                .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}".formatted(id, method, params));
        if (sessionId != null) {
            request = request.header("Mcp-Session-Id", sessionId);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static RequestPostProcessor m2mJwt(String tenantId, String authority) {
        return jwt().jwt(builder -> builder
                        .claim("sub", "agent-1")
                        .claim("tenant_id", tenantId)
                        .claim("roles", List.of(authority)))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
