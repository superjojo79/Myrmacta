package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class StartWorkflowToolTest {

    private static final String TENANT = "tenant_dev";

    private GetDossierUseCase getDossierUseCase;
    private DossierAccessPolicy accessPolicy;
    private ManualStartOrchestrator orchestrator;
    private StartWorkflowTool tool;
    private UUID dossierId;

    @BeforeEach
    void setUp() {
        getDossierUseCase = mock(GetDossierUseCase.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        orchestrator = mock(ManualStartOrchestrator.class);
        tool = new StartWorkflowTool(getDossierUseCase, accessPolicy, orchestrator, new McpAccessContextFactory());
        dossierId = UUID.randomUUID();
        when(getDossierUseCase.get(eq(dossierId), any())).thenReturn(dossier(dossierId));
        bindJwt();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void start_delegatesToOrchestrator() {
        DossierWorkflowLink link = link(dossierId, "proc-1");
        when(orchestrator.start(eq(dossierId), eq("wf-sinistre"), eq(Map.of("foo", "bar")), eq("idem-1"), eq("agent-1")))
                .thenReturn(new ManualStartOrchestrator.StartResult(link, false));

        Object result = tool.startWorkflow(
                dossierId.toString(), "wf-sinistre", "idem-1", Map.of("foo", "bar"), false);

        verify(accessPolicy).assertCanEdit(any(), any());
        assertThat(result).isInstanceOf(StartWorkflowTool.Result.class);
        StartWorkflowTool.Result typed = (StartWorkflowTool.Result) result;
        assertThat(typed.processInstanceId()).isEqualTo("proc-1");
        assertThat(typed.replay()).isFalse();
        assertThat(typed.startedBy()).isEqualTo("starter");
    }

    @Test
    void blankIdempotencyKey_isRejected() {
        assertThatThrownBy(() -> tool.startWorkflow(dossierId.toString(), "wf", "  ", Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey is required");
        verify(orchestrator, never()).start(any(), any(), any(), any(), any());
    }

    @Test
    void reservedVariable_isRejected() {
        assertThatThrownBy(() ->
                        tool.startWorkflow(dossierId.toString(), "wf", "idem-1", Map.of("tenantId", "x"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(orchestrator, never()).start(any(), any(), any(), any(), any());
    }

    @Test
    void editDenied_doesNotStart() {
        doThrow(new AccessDeniedException("no edit")).when(accessPolicy).assertCanEdit(any(), any());

        assertThatThrownBy(() ->
                        tool.startWorkflow(dossierId.toString(), "wf-sinistre", "idem-1", Map.of(), false))
                .isInstanceOf(AccessDeniedException.class);
        verify(orchestrator, never()).start(any(), any(), any(), any(), any());
    }

    @Test
    void replay_isExposedOnResult() {
        when(orchestrator.start(any(), any(), any(), any(), any()))
                .thenReturn(new ManualStartOrchestrator.StartResult(link(dossierId, "proc-1"), true));

        StartWorkflowTool.Result result = (StartWorkflowTool.Result)
                tool.startWorkflow(dossierId.toString(), "wf-sinistre", "idem-1", Map.of(), false);

        assertThat(result.replay()).isTrue();
    }

    @Test
    void dryRun_doesNotStart() {
        @SuppressWarnings("unchecked")
        Map<String, Object> preview = (Map<String, Object>)
                tool.startWorkflow(dossierId.toString(), "wf-sinistre", "idem-1", Map.of(), true);

        assertThat(preview).containsEntry("dryRun", true).containsEntry("processDefinitionKey", "wf-sinistre");
        verify(orchestrator, never()).start(any(), any(), any(), any(), any());
        verify(orchestrator).bindStartVariables("wf-sinistre", Map.of());
        verify(accessPolicy).assertCanEdit(any(), any());
    }

    private static void bindJwt() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Dossier dossier(UUID id) {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        return new Dossier(
                id, TENANT, "REF-1", "SINISTRE", "Acme", DossierStatus.OUVERT,
                Map.of(), null, 0L, "creator", now, "creator", now, null, null, null);
    }

    private static DossierWorkflowLink link(UUID dossierId, String processInstanceId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                processInstanceId,
                "wf-sinistre",
                1,
                "STARTED",
                "manual:idem-1",
                Instant.parse("2026-09-08T00:00:00Z"),
                null,
                null,
                "starter");
    }
}
