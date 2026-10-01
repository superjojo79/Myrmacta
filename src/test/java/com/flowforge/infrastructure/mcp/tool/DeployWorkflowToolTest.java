package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import com.flowforge.domain.workflow.model.PublicationMode;
import com.flowforge.domain.workflow.port.in.ManageDraftsUseCase;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.PublicationPolicy;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

/** TODO §82 (D6) — {@code deploy_workflow} écrit un brouillon ; la publication directe n'a lieu que si la politique l'autorise. */
class DeployWorkflowToolTest {

    private static final String KEY = "claimProcess";
    private static final Instant STAMP = Instant.parse("2026-10-01T10:00:00Z");

    private BpmnParserPort bpmnParserPort;
    private ProposeDraftUseCase propose;
    private ManageDraftsUseCase manage;
    private boolean direct;
    private DeployWorkflowTool tool;

    @BeforeEach
    void setUp() {
        bpmnParserPort = mock(BpmnParserPort.class);
        propose = mock(ProposeDraftUseCase.class);
        manage = mock(ManageDraftsUseCase.class);
        direct = false;
        tool = newTool(DataSize.ofMegabytes(25));
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private DeployWorkflowTool newTool(DataSize max) {
        PublicationPolicy policy = () -> direct;
        return new DeployWorkflowTool(bpmnParserPort, propose, manage, policy, new McpAccessContextFactory(),
                JsonMapper.builder().build(), max);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void byDefaultTheProposalIsSavedAsADraftAndNothingIsPublished() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(propose.proposeBpmn(any(), eq(Provenance.MCP), eq(KEY), any(), any(), anyBoolean()))
                .thenReturn(new ProposeDraftUseCase.Proposal(DraftArtifactType.BPMN, KEY, STAMP, false));

        Map<String, Object> result = invoke(baseArgs());

        assertThat(result).containsEntry("status", "DRAFT_PENDING_PUBLICATION").containsEntry("dryRun", false);
        assertThat(result.get("message").toString()).contains("human");
        verifyNoInteractions(manage);
    }

    @Test
    void theSubmittedConfigurationIsCarriedWithTheDraft() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(propose.proposeBpmn(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ProposeDraftUseCase.Proposal(DraftArtifactType.BPMN, KEY, STAMP, false));
        Map<String, Object> args = baseArgs();
        args.put("onStartDossierStatus", "OUVERT");
        args.put("observerUserIds", List.of("user-1", "user-2"));

        invoke(args);

        var captor = ArgumentCaptor.forClass(ProposedWorkflowConfig.class);
        verify(propose).proposeBpmn(any(), any(), eq(KEY), any(), captor.capture(), eq(false));
        assertThat(captor.getValue().label()).isEqualTo("Claim");
        assertThat(captor.getValue().onStartDossierStatus()).isEqualTo(com.flowforge.domain.dossier.model.DossierStatus.OUVERT);
        assertThat(captor.getValue().observerUserIds()).containsExactly("user-1", "user-2");
    }

    @Test
    void replaceExistingDraftIsForwardedOnlyWhenExplicitlyTrue() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(propose.proposeBpmn(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ProposeDraftUseCase.Proposal(DraftArtifactType.BPMN, KEY, STAMP, true));
        Map<String, Object> args = baseArgs();
        args.put("replaceExistingDraft", true);
        invoke(args);
        verify(propose).proposeBpmn(any(), any(), any(), any(), any(), eq(true));
    }

    @Test
    void whenTheInstanceAllowsDirectPublicationTheSameDraftIsPublishedThroughTheSameUseCase() {
        direct = true;
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        when(propose.proposeBpmn(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ProposeDraftUseCase.Proposal(DraftArtifactType.BPMN, KEY, STAMP, false));
        ConfigPublication publication = new ConfigPublication(UUID.randomUUID(), "t", DraftArtifactType.BPMN, KEY, 3,
                Provenance.MCP, "agent-1", "agent-1", STAMP, PublicationMode.DIRECT);
        when(manage.publish(any(), eq(DraftArtifactType.BPMN), eq(KEY), eq(STAMP), eq(PublicationMode.DIRECT))).thenReturn(publication);

        Map<String, Object> result = invoke(baseArgs());

        assertThat(result).containsEntry("status", "PUBLISHED").containsEntry("publication", publication);
    }

    @Test
    void dryRunValidatesWithoutWritingAnything() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        Map<String, Object> args = baseArgs();
        args.put("dryRun", true);

        Map<String, Object> result = invoke(args);

        assertThat(result).containsEntry("dryRun", true).containsEntry("status", "VALID");
        verify(propose).validateBpmn(any(), eq(KEY), any(), any());
        verify(propose, never()).proposeBpmn(any(), any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(manage);
    }

    @Test
    void invalidXmlAndMismatchedKeyAreRejectedBeforeAnyProposal() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenThrow(new InvalidBpmnException("bad xml"));
        assertThatThrownBy(() -> invoke(baseArgs())).isInstanceOf(InvalidBpmnException.class);

        org.mockito.Mockito.doReturn("differentKey").when(bpmnParserPort).extractProcessDefinitionKey(any());
        assertThatThrownBy(() -> invoke(baseArgs()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("differentKey");
        verifyNoInteractions(propose, manage);
    }

    @Test
    void xmlExceedingMaxSizeIsRejectedBeforeParsing() {
        DeployWorkflowTool tiny = newTool(DataSize.ofBytes(4));
        assertThatThrownBy(() -> tiny.deployWorkflow(request(baseArgs()), null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds max size");
        verifyNoInteractions(bpmnParserPort, propose, manage);
    }

    @Test
    void malformedArgumentsAreRejected() {
        when(bpmnParserPort.extractProcessDefinitionKey(any())).thenReturn(KEY);
        for (String field : List.of("statusDefinitions", "allowedDossierStatuses", "observerUserIds")) {
            Map<String, Object> args = baseArgs();
            args.put(field, "not-a-list");
            assertThatThrownBy(() -> invoke(args)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(field);
        }
        Map<String, Object> empty = baseArgs();
        empty.put("statusDefinitions", List.of());
        assertThatThrownBy(() -> invoke(empty)).hasMessageContaining("statusDefinitions");
        Map<String, Object> noStatuses = baseArgs();
        noStatuses.put("allowedDossierStatuses", List.of());
        assertThatThrownBy(() -> invoke(noStatuses)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> baseArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("processDefinitionKey", KEY);
        args.put("xml", "<definitions/>");
        args.put("label", "Claim");
        args.put("statusDefinitions", List.of(Map.of("code", "STARTED", "label", "Started", "initial", true, "outcome", "NONE")));
        args.put("allowedDossierStatuses", List.of("OUVERT"));
        return args;
    }

    private static McpSchema.CallToolRequest request(Map<String, Object> args) {
        return new McpSchema.CallToolRequest("deploy_workflow", args);
    }

    private Map<String, Object> invoke(Map<String, Object> args) {
        return tool.deployWorkflow(request(args), null, null, null, null, null, null, null, null, null);
    }
}
