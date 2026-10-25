package be.myrmacta.infrastructure.mcp.tool;

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

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.PublicationMode;
import be.myrmacta.domain.workflow.port.in.ManageDraftsUseCase;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import be.myrmacta.infrastructure.mcp.McpActorScope;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** TODO §82 (D6) — {@code deploy_decision_table} et {@code redeploy_decision_table} écrivent un brouillon DMN. */
class DeployDecisionTableToolTest {

    private static final String KEY = "testDecision";
    private static final String XML = "<dmn/>";
    private static final Instant STAMP = Instant.parse("2026-10-01T10:00:00Z");
    private static final AccessContext.User ACTOR = AccessContext.user("agent-1", Set.of("API_SERVICE"));

    private DmnParserPort dmnParserPort;
    private ProposeDraftUseCase propose;
    private ManageDraftsUseCase manage;
    private boolean direct;
    private McpAccessContextFactory factory;

    @BeforeEach
    void setUp() {
        dmnParserPort = mock(DmnParserPort.class);
        propose = mock(ProposeDraftUseCase.class);
        manage = mock(ManageDraftsUseCase.class);
        direct = false;
        factory = new McpAccessContextFactory();
    }

    private DeployDecisionTableTool deployTool(DataSize max) {
        return new DeployDecisionTableTool(dmnParserPort, propose, manage, () -> direct, factory, max);
    }

    private RedeployDecisionTableTool redeployTool() {
        return new RedeployDecisionTableTool(propose, manage, () -> direct, factory);
    }

    private <T> T asAgent(java.util.function.Supplier<T> action) {
        return McpActorScope.call(ACTOR, action);
    }

    @Test
    void deployWritesADraftByDefaultAndPublishesNothing() {
        when(dmnParserPort.extractDecisionKey(any())).thenReturn(KEY);
        when(propose.proposeDmn(any(), any(), eq(KEY), eq(XML), any(), anyBoolean()))
                .thenReturn(new ProposeDraftUseCase.Proposal(DraftArtifactType.DMN, KEY, STAMP, false));

        Map<String, Object> result = asAgent(() -> deployTool(DataSize.ofMegabytes(25)).deployDecisionTable(KEY, XML, false, null));

        assertThat(result).containsEntry("status", "DRAFT_PENDING_PUBLICATION").containsEntry("decisionKey", KEY);
        // portée in-process : l'écriture est attribuée à l'assistant
        verify(propose).proposeDmn(any(), eq(Provenance.ASSISTANT), eq(KEY), eq(XML), any(), eq(false));
        verifyNoInteractions(manage);
    }

    @Test
    void directPublicationGoesThroughTheSamePublishUseCase() {
        direct = true;
        when(dmnParserPort.extractDecisionKey(any())).thenReturn(KEY);
        when(propose.proposeDmn(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ProposeDraftUseCase.Proposal(DraftArtifactType.DMN, KEY, STAMP, false));
        ConfigPublication publication = new ConfigPublication(UUID.randomUUID(), "t", DraftArtifactType.DMN, KEY, 2,
                Provenance.ASSISTANT, "agent-1", "agent-1", STAMP, PublicationMode.DIRECT);
        when(manage.publish(any(), eq(DraftArtifactType.DMN), eq(KEY), eq(STAMP), eq(PublicationMode.DIRECT))).thenReturn(publication);

        Map<String, Object> result = asAgent(() -> deployTool(DataSize.ofMegabytes(25)).deployDecisionTable(KEY, XML, null, null));

        assertThat(result).containsEntry("status", "PUBLISHED").containsEntry("publication", publication);
    }

    @Test
    void dryRunValidatesWithoutWriting() {
        when(dmnParserPort.extractDecisionKey(any())).thenReturn(KEY);

        Map<String, Object> result = asAgent(() -> deployTool(DataSize.ofMegabytes(25)).deployDecisionTable(KEY, XML, true, null));

        assertThat(result).containsEntry("dryRun", true).containsEntry("status", "VALID");
        verify(propose).validateDmn(any(), eq(KEY), eq(XML));
        verify(propose, never()).proposeDmn(any(), any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(manage);
    }

    @Test
    void mismatchedKeyInvalidDmnAndOversizeAreRejectedBeforeAnyProposal() {
        when(dmnParserPort.extractDecisionKey(any())).thenReturn("otherDecision");
        assertThatThrownBy(() -> deployTool(DataSize.ofMegabytes(25)).deployDecisionTable(KEY, XML, false, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("otherDecision");
        when(dmnParserPort.extractDecisionKey(any())).thenThrow(new InvalidDmnException("bad dmn"));
        assertThatThrownBy(() -> deployTool(DataSize.ofMegabytes(25)).deployDecisionTable(KEY, XML, false, null))
                .isInstanceOf(InvalidDmnException.class);
        assertThatThrownBy(() -> deployTool(DataSize.ofBytes(4)).deployDecisionTable(KEY, XML, false, null))
                .hasMessageContaining("exceeds max size");
        verifyNoInteractions(propose, manage);
    }

    @Test
    void redeployLoadsTheVersionIntoADraftAndRequiresAVersion() {
        when(propose.proposeDmnFromVersion(any(), any(), eq("eligibilite"), eq(1), eq(false)))
                .thenReturn(new ProposeDraftUseCase.Proposal(DraftArtifactType.DMN, "eligibilite", STAMP, false));

        Map<String, Object> result = asAgent(() -> redeployTool().redeployDecisionTable("eligibilite", 1, null));

        assertThat(result).containsEntry("status", "DRAFT_PENDING_PUBLICATION");
        verifyNoInteractions(manage);
        assertThatThrownBy(() -> asAgent(() -> redeployTool().redeployDecisionTable("eligibilite", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
