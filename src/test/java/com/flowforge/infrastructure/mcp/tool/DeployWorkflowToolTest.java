package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

class DeployWorkflowToolTest {

    private static final String TENANT = "tenant_dev";
    private static final String KEY = "claimProcess";

    private BpmnParserPort bpmnParserPort;
    private GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase;
    private SaveWorkflowTypeConfigUseCase saveWorkflowTypeConfigUseCase;
    private DeployBpmnProcessUseCase deployBpmnProcessUseCase;
    private DeployWorkflowTool tool;

    @BeforeEach
    void setUp() {
        bpmnParserPort = mock(BpmnParserPort.class);
        getWorkflowTypeConfigUseCase = mock(GetWorkflowTypeConfigUseCase.class);
        saveWorkflowTypeConfigUseCase = mock(SaveWorkflowTypeConfigUseCase.class);
        deployBpmnProcessUseCase = mock(DeployBpmnProcessUseCase.class);
        tool = new DeployWorkflowTool(
                bpmnParserPort,
                getWorkflowTypeConfigUseCase,
                saveWorkflowTypeConfigUseCase,
                deployBpmnProcessUseCase,
                new TenantProperties(TENANT),
                new McpAccessContextFactory(),
                JsonMapper.builder().build(),
                DataSize.ofMegabytes(25));

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validXml_upsertsConfigThenDeploysInOrder() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(getWorkflowTypeConfigUseCase.getByKey(KEY)).thenReturn(Optional.empty());
        when(saveWorkflowTypeConfigUseCase.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(deployBpmnProcessUseCase.deploy(eq(KEY + ".bpmn20.xml"), any()))
                .thenReturn(new BpmnDeploymentResult(KEY, 1, "dep-1"));

        Map<String, Object> result = invoke(baseArgs());

        assertThat(result.get("processDefinitionKey")).isEqualTo(KEY);
        InOrder order = inOrder(saveWorkflowTypeConfigUseCase, deployBpmnProcessUseCase);
        order.verify(saveWorkflowTypeConfigUseCase).create(any(), any());
        order.verify(deployBpmnProcessUseCase).deploy(eq(KEY + ".bpmn20.xml"), any());
    }

    @Test
    void refusedReference_isRejectedBeforeAnyConfigWrite() {
        // revue M5 : plus de configuration modifiée sans BPMN déployé
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(getWorkflowTypeConfigUseCase.getByKey(KEY)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new com.flowforge.domain.workflow.model.UnknownBlockReferenceException("act", "documentType", "x"))
                .when(deployBpmnProcessUseCase).validate(any(), any());

        assertThatThrownBy(() -> invoke(baseArgs()))
                .isInstanceOf(com.flowforge.domain.workflow.model.UnknownBlockReferenceException.class);
        verifyNoInteractions(saveWorkflowTypeConfigUseCase);
        org.mockito.Mockito.verify(deployBpmnProcessUseCase, org.mockito.Mockito.never()).deploy(any(), any());
    }

    @Test
    void invalidXml_rejectedBeforeAnyConfigChange() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenThrow(new InvalidBpmnException("bad xml"));

        assertThatThrownBy(() -> invoke(baseArgs()))
                .isInstanceOf(InvalidBpmnException.class);
        verifyNoInteractions(getWorkflowTypeConfigUseCase, saveWorkflowTypeConfigUseCase, deployBpmnProcessUseCase);
    }

    @Test
    void dryRun_parsesButDoesNotSaveOrDeploy() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(getWorkflowTypeConfigUseCase.getByKey(KEY)).thenReturn(Optional.empty());

        Map<String, Object> args = baseArgs();
        args.put("dryRun", true);
        Map<String, Object> result = invoke(args);

        assertThat(result.get("dryRun")).isEqualTo(true);
        // S-DRY-1/2 : mêmes contrôles que le déploiement, avec la configuration soumise ; rien n'est écrit ni déployé
        verify(deployBpmnProcessUseCase).validate(any(), any());
        verify(deployBpmnProcessUseCase, never()).deploy(any(), any());
        verifyNoInteractions(saveWorkflowTypeConfigUseCase);
    }

    @Test
    void mismatchedProcessDefinitionKey_isRejected() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn("differentKey");

        assertThatThrownBy(() -> invoke(baseArgs()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("differentKey");
        verifyNoInteractions(getWorkflowTypeConfigUseCase, saveWorkflowTypeConfigUseCase, deployBpmnProcessUseCase);
    }

    @Test
    void emptyAllowedDossierStatuses_isRejectedByConfigInvariant() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(getWorkflowTypeConfigUseCase.getByKey(KEY)).thenReturn(Optional.empty());

        Map<String, Object> args = baseArgs();
        args.put("allowedDossierStatuses", List.of());

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedDossierStatuses");
        verifyNoInteractions(saveWorkflowTypeConfigUseCase, deployBpmnProcessUseCase);
    }

    @Test
    void existingConfig_preservesCreatedByAndCallsUpdate() {
        WorkflowTypeConfig existing = new WorkflowTypeConfig(
                UUID.randomUUID(), TENANT, KEY, "Old",
                List.of(new com.flowforge.domain.workflow.model.WorkflowStatusDefinition(
                        "STARTED", "Started", true, com.flowforge.domain.workflow.model.StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT), null, List.of(), "original-author", "original-author");
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(getWorkflowTypeConfigUseCase.getByKey(KEY)).thenReturn(Optional.of(existing));
        when(saveWorkflowTypeConfigUseCase.update(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(deployBpmnProcessUseCase.deploy(any(), any())).thenReturn(new BpmnDeploymentResult(KEY, 2, "dep-2"));

        invoke(baseArgs());

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowTypeConfig.class);
        org.mockito.Mockito.verify(saveWorkflowTypeConfigUseCase).update(captor.capture(), any());
        assertThat(captor.getValue().createdBy()).isEqualTo("original-author");
        assertThat(captor.getValue().updatedBy()).isEqualTo("agent-1");
    }

    @Test
    void onStartDossierStatusProvided_isAppliedToConfig() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(getWorkflowTypeConfigUseCase.getByKey(KEY)).thenReturn(Optional.empty());
        when(saveWorkflowTypeConfigUseCase.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(deployBpmnProcessUseCase.deploy(any(), any())).thenReturn(new BpmnDeploymentResult(KEY, 1, "dep-1"));

        Map<String, Object> args = baseArgs();
        args.put("onStartDossierStatus", "OUVERT");
        invoke(args);

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowTypeConfig.class);
        org.mockito.Mockito.verify(saveWorkflowTypeConfigUseCase).create(captor.capture(), any());
        assertThat(captor.getValue().onStartDossierStatus()).isEqualTo(DossierStatus.OUVERT);
    }

    @Test
    void xmlExceedingMaxSize_isRejectedBeforeParsing() {
        DeployWorkflowTool tinyLimitTool = new DeployWorkflowTool(
                bpmnParserPort,
                getWorkflowTypeConfigUseCase,
                saveWorkflowTypeConfigUseCase,
                deployBpmnProcessUseCase,
                new TenantProperties(TENANT),
                new McpAccessContextFactory(),
                JsonMapper.builder().build(),
                DataSize.ofBytes(4));

        Map<String, Object> args = baseArgs();
        assertThatThrownBy(() -> tinyLimitTool.deployWorkflow(request(args), null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds max size");
        verifyNoInteractions(bpmnParserPort, getWorkflowTypeConfigUseCase, saveWorkflowTypeConfigUseCase, deployBpmnProcessUseCase);
    }

    @Test
    void statusDefinitionsEmpty_isRejected() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);

        Map<String, Object> args = baseArgs();
        args.put("statusDefinitions", List.of());

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusDefinitions");
    }

    @Test
    void statusDefinitionsWrongType_isRejected() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);

        Map<String, Object> args = baseArgs();
        args.put("statusDefinitions", "not-a-list");

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusDefinitions");
    }

    @Test
    void allowedDossierStatusesWrongType_isRejected() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);

        Map<String, Object> args = baseArgs();
        args.put("allowedDossierStatuses", "not-a-list");

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedDossierStatuses");
    }

    @Test
    void observerUserIdsProvided_isRelayedToConfig() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(getWorkflowTypeConfigUseCase.getByKey(KEY)).thenReturn(Optional.empty());
        when(saveWorkflowTypeConfigUseCase.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(deployBpmnProcessUseCase.deploy(any(), any())).thenReturn(new BpmnDeploymentResult(KEY, 1, "dep-1"));

        Map<String, Object> args = baseArgs();
        args.put("observerUserIds", List.of("user-1", "user-2"));
        invoke(args);

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowTypeConfig.class);
        org.mockito.Mockito.verify(saveWorkflowTypeConfigUseCase).create(captor.capture(), any());
        assertThat(captor.getValue().observerUserIds()).containsExactly("user-1", "user-2");
    }

    @Test
    void observerUserIdsWrongType_isRejected() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);

        Map<String, Object> args = baseArgs();
        args.put("observerUserIds", "not-a-list");

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observerUserIds");
    }

    private static Map<String, Object> baseArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("processDefinitionKey", KEY);
        args.put("xml", "<definitions/>");
        args.put("label", "Claim");
        args.put("statusDefinitions", List.of(Map.of(
                "code", "STARTED", "label", "Started", "initial", true, "outcome", "NONE")));
        args.put("allowedDossierStatuses", List.of("OUVERT"));
        return args;
    }

    private static McpSchema.CallToolRequest request(Map<String, Object> args) {
        return new McpSchema.CallToolRequest("deploy_workflow", args);
    }

    private Map<String, Object> invoke(Map<String, Object> args) {
        return tool.deployWorkflow(request(args), null, null, null, null, null, null, null, null);
    }
}
