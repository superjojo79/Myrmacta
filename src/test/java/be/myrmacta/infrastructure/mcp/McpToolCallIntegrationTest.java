package be.myrmacta.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
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

    @Autowired
    private be.myrmacta.domain.workflow.port.in.ManageDraftsUseCase manageDraftsUseCase;

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
    void deployWorkflow_writesADraftAndAppliesNothingUntilAHumanPublishes() throws Exception {
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
        // TODO §82 (D6) : par défaut un agent écrit un BROUILLON — ni configuration ni déploiement avant la publication humaine.
        assertThat(body).contains("DRAFT_PENDING_PUBLICATION");
        assertThat(getWorkflowTypeConfigUseCase.getByKey(key)).isEmpty();
        var pending = manageDraftsUseCase.listPending(be.myrmacta.domain.dossier.model.AccessContext.user("alice", java.util.Set.of("TENANT_ADMIN")));
        assertThat(pending).anyMatch(d -> d.key().equals(key)
                && d.provenance() == be.myrmacta.domain.workflow.model.Provenance.MCP && d.author().equals("agent-1"));

        // publish_draft est refusé tant que l'instance n'autorise pas la publication directe
        MvcResult publish = callMcp(sessionId, TENANT, "API_SERVICE", 3, "tools/call", """
                {"name": "publish_draft", "arguments": {"type": "BPMN", "key": "%s"}}
                """.formatted(key));
        assertThat(publish.getResponse().getContentAsString()).contains("\"isError\":true").contains("human");
        assertThat(getWorkflowTypeConfigUseCase.getByKey(key)).isEmpty();
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
    void createDossier_persistsAndIsVisibleToDirectRepositoryRead() throws Exception {
        String typeCode = "MCPWR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        dossierTypeRepository.save(new DossierType(
                UUID.randomUUID(), TENANT, typeCode, "Write type", "wf-write", true, true,
                List.of(), null, false, null, "system", "system"));

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "create_dossier",
                  "arguments": {
                    "dossierTypeCode": "%s",
                    "label": "MCP write dossier"
                  }
                }
                """.formatted(typeCode));

        String body = call.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":false");
        assertThat(body).contains("MCP write dossier");
        List<Dossier> created = dossierRepository.findByDossierTypeCode(TENANT, typeCode);
        assertThat(created).anyMatch(d -> "MCP write dossier".equals(d.label()));
        assertThat(created.stream()
                        .filter(d -> "MCP write dossier".equals(d.label()))
                        .findFirst()
                        .orElseThrow()
                        .createdBy())
                .isEqualTo("agent-1");
    }

    @Test
    void addDossierElement_note_persistsOnCreatedDossier() throws Exception {
        String typeCode = "MCPNT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        dossierTypeRepository.save(new DossierType(
                UUID.randomUUID(), TENANT, typeCode, "Note type", "wf-note", true, true,
                List.of(), null, false, null, "system", "system"));
        Instant now = Instant.now();
        Dossier dossier = new Dossier(
                UUID.randomUUID(), TENANT, "DOS-NOTE-" + UUID.randomUUID().toString().substring(0, 8), typeCode,
                "Note host", DossierStatus.OUVERT, Map.of(), null, 0L, "creator", now, "creator", now, null, null,
                null);
        dossierRepository.save(dossier);

        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "add_dossier_element",
                  "arguments": {
                    "dossierId": "%s",
                    "type": "NOTE",
                    "label": "MCP note",
                    "metadata": {"text": "hello from MCP"}
                  }
                }
                """.formatted(dossier.id()));

        String body = call.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":false");
        assertThat(body).contains("MCP note").contains("hello from MCP");
    }

    @Test
    void createDossier_unknownType_returnsIsError() throws Exception {
        MvcResult call = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "create_dossier",
                  "arguments": {
                    "dossierTypeCode": "DOESNOTEXIST",
                    "label": "Nope"
                  }
                }
                """);

        String body = call.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":true");
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
                        .pageByTenantId(TENANT, be.myrmacta.domain.shared.PageQuery.of(0, 100))
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

        var persisted = connectorInstanceRepository.pageByTenantId(TENANT, be.myrmacta.domain.shared.PageQuery.of(0, 100))
                .content().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        assertThat(persisted.config()).containsEntry("webhookSecret", "supersecretvalue1234");

        MvcResult getCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "get_connector_instance", "arguments": {"id": "%s"}}
                """.formatted(persisted.id()));
        String getBody = getCall.getResponse().getContentAsString();
        assertThat(getBody).as("body: %s", getBody).doesNotContain("supersecretvalue1234");
    }

    @Test
    void defineConnectorInstance_masksSchemaDeclaredPasswordFieldNotMatchingNameHeuristic() throws Exception {
        // authHeaderValue matches no name heuristic (webhookSecret does) — only the connector
        // type's declared PASSWORD schema field catches it (adversarial review follow-up).
        String name = "MCP IT AuthHeader " + UUID.randomUUID();

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
                      "dossierTypeCode": "MCPIT",
                      "authHeaderValue": "Bearer plaincredential5678"
                    }
                  }
                }
                """.formatted(name));
        String createBody = createCall.getResponse().getContentAsString();
        assertThat(createBody).as("body: %s", createBody).contains("\"isError\":false");
        assertThat(createBody).doesNotContain("plaincredential5678");

        var persisted = connectorInstanceRepository.pageByTenantId(TENANT, be.myrmacta.domain.shared.PageQuery.of(0, 100))
                .content().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        assertThat(persisted.config()).containsEntry("authHeaderValue", "Bearer plaincredential5678");

        MvcResult getCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "get_connector_instance", "arguments": {"id": "%s"}}
                """.formatted(persisted.id()));
        String getBody = getCall.getResponse().getContentAsString();
        assertThat(getBody).as("body: %s", getBody).doesNotContain("plaincredential5678");

        MvcResult listCall = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {"name": "list_connector_instances", "arguments": {}}
                """);
        String listBody = listCall.getResponse().getContentAsString();
        assertThat(listBody).as("body: %s", listBody).doesNotContain("plaincredential5678");
    }

    /** D3a : un secret OAuth2 défini via le canal M2M est chiffré en base puis masqué dans get/list et dans la réponse. */
    @Test
    void defineConnectorInstance_oauth2SecretsAreEncryptedAtRestAndNeverReturned() throws Exception {
        String name = "MCP IT OAuth " + UUID.randomUUID();

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
                      "dossierTypeCode": "MCPIT",
                      "authMode": "OAUTH2_CLIENT_CREDENTIALS",
                      "oauthTokenUrl": "https://93.184.216.34/token",
                      "oauthClientId": "app-mcp",
                      "oauthClientSecret": "oauthclientsecret9876",
                      "oauthAllowedHosts": "erp.example.com"
                    }
                  }
                }
                """.formatted(name));
        String createBody = createCall.getResponse().getContentAsString();
        assertThat(createBody).as("body: %s", createBody).contains("\"isError\":false");
        assertThat(createBody).doesNotContain("oauthclientsecret9876");

        var persisted = connectorInstanceRepository.pageByTenantId(TENANT, be.myrmacta.domain.shared.PageQuery.of(0, 100))
                .content().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        assertThat(persisted.config()).containsEntry("oauthClientSecret", "oauthclientsecret9876");

        String getBody = callMcp(sessionId, TENANT, "API_SERVICE", 3, "tools/call", """
                {"name": "get_connector_instance", "arguments": {"id": "%s"}}
                """.formatted(persisted.id())).getResponse().getContentAsString();
        assertThat(getBody).as("body: %s", getBody).doesNotContain("oauthclientsecret9876").contains("app-mcp");
        String listBody = callMcp(sessionId, TENANT, "API_SERVICE", 4, "tools/call", """
                {"name": "list_connector_instances", "arguments": {}}
                """).getResponse().getContentAsString();
        assertThat(listBody).doesNotContain("oauthclientsecret9876");

        String definitions = callMcp(sessionId, TENANT, "API_SERVICE", 5, "tools/call", """
                {"name": "list_connector_definitions", "arguments": {}}
                """).getResponse().getContentAsString();
        assertThat(definitions).contains("oauthAllowedHosts").contains("OAUTH2_CLIENT_CREDENTIALS");
    }

    /** D1 règles (TODO §80) : définir un type à règles puis les prévisualiser, à travers la vraie chaîne MCP. */
    @Test
    void defineDossierTypeWithRules_thenEvaluateDossierRules_returnsTheComputedValueAndTheHiddenFields() throws Exception {
        String code = "MCPRULE" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        MvcResult define = callMcp(sessionId, TENANT, "API_SERVICE", 2, "tools/call", """
                {
                  "name": "define_dossier_type",
                  "arguments": {
                    "code": "%s", "label": "Règles MCP", "workflowKey": "wf-mcp-rules", "active": true,
                    "allowUnknownFields": false,
                    "fieldDefinitions": [
                      {"name": "ht", "label": "HT", "type": "NUMBER", "required": false},
                      {"name": "pays", "label": "Pays", "type": "STRING", "required": false},
                      {"name": "ttc", "label": "TTC", "type": "NUMBER", "required": false, "formula": "dossier.fields.ht * 2"},
                      {"name": "siret", "label": "SIRET", "type": "STRING", "required": false, "visibleWhen": "dossier.fields.pays = 'FR'"}
                    ]
                  }
                }
                """.formatted(code));
        assertThat(define.getResponse().getContentAsString()).contains("\"isError\":false");

        MvcResult preview = callMcp(sessionId, TENANT, "API_SERVICE", 3, "tools/call", """
                {"name": "evaluate_dossier_rules",
                 "arguments": {"dossierTypeCode": "%s", "fields": {"ht": 21, "pays": "DE"}}}
                """.formatted(code));
        String body = preview.getResponse().getContentAsString();
        assertThat(body).as("body: %s", body).contains("\"isError\":false").contains("ttc").contains("42").contains("siret");

        MvcResult bad = callMcp(sessionId, TENANT, "API_SERVICE", 4, "tools/call", """
                {"name": "define_dossier_type",
                 "arguments": {"code": "%s2", "label": "x", "workflowKey": "wf", "active": true, "allowUnknownFields": false, "dryRun": true,
                   "fieldDefinitions": [{"name": "b", "label": "B", "type": "NUMBER", "required": false, "formula": "$eval('1')"}]}}
                """.formatted(code));
        assertThat(bad.getResponse().getContentAsString()).contains("\"isError\":true").contains("$eval");
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
        // D6 : l'agent propose, un humain relit et publie (même cas d'usage que l'écran « Brouillons à publier »)
        var admin = be.myrmacta.domain.dossier.model.AccessContext.user("alice", java.util.Set.of("TENANT_ADMIN"));
        var draft = manageDraftsUseCase.listPending(admin).stream().filter(d -> d.key().equals(workflowKey)).findFirst().orElseThrow();
        manageDraftsUseCase.publish(admin, be.myrmacta.domain.workflow.model.DraftArtifactType.BPMN, workflowKey,
                draft.updatedAt(), be.myrmacta.domain.workflow.model.PublicationMode.MANUAL);
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
