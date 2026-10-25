package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.parcours.model.CompiledParcours;
import be.myrmacta.domain.parcours.model.InvalidParcoursException;
import be.myrmacta.domain.parcours.model.Parcours;
import be.myrmacta.domain.parcours.model.ParcoursDefinition;
import be.myrmacta.domain.parcours.port.in.CompileParcoursUseCase;
import be.myrmacta.domain.parcours.port.in.ManageParcoursUseCase;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/** TODO §88 (LC5) — outils MCP des parcours : dry-run fidèle au déploiement, jamais de publication, conversion des arguments. */
class ParcoursToolsTest {

    private ManageParcoursUseCase manage;
    private CompileParcoursUseCase compile;
    private ParcoursTools tools;

    private static final List<Map<String, Object>> STATUSES = List.of(
            Map.of("code", "OUVERT", "label", "Ouvert", "initial", true, "outcome", "NONE"),
            Map.of("code", "FIN", "label", "Fin", "initial", false, "outcome", "COMPLETED"));
    private static final List<Map<String, Object>> STEPS = List.of(
            Map.of("id", "saisie", "kind", "QUESTION", "params", Map.of("schemaKey", "avis")),
            Map.of("id", "fin", "kind", "SET_WORKFLOW_STATUS", "params", Map.of("targetWorkflowStatus", "FIN")));

    @BeforeEach
    void setUp() {
        manage = mock(ManageParcoursUseCase.class);
        compile = mock(CompileParcoursUseCase.class);
        tools = new ParcoursTools(manage, compile, new McpAccessContextFactory(), JsonMapper.builder().build());
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CompiledParcours compiled() {
        return new CompiledParcours("<definitions/>", "a".repeat(64), List.of("avertissement"), 2);
    }

    @Test
    void aDryRunIsValidOnlyWhenTheDeploymentWouldAcceptTheBpmn() {
        when(compile.preview(any(), eq("p"), eq("P"), any())).thenReturn(new CompileParcoursUseCase.Preview(compiled(), "unknown connector x"));
        Map<String, Object> out = tools.defineParcours("p", "P", STATUSES, List.of("OUVERT"), null, STEPS, null, null, true);
        assertThat(out).containsEntry("dryRun", true).containsEntry("valid", false).containsEntry("problem", "unknown connector x");
        verify(manage, never()).save(any(), any());

        when(compile.preview(any(), eq("p"), eq("P"), any())).thenReturn(new CompileParcoursUseCase.Preview(compiled(), null));
        assertThat(tools.defineParcours("p", "P", STATUSES, List.of("OUVERT"), null, STEPS, null, null, true))
                .containsEntry("valid", true).doesNotContainKey("problem");
    }

    @Test
    void defineSavesWithTheExpectedVersionAndAnEmptyOnStartStatusMeansNone() {
        Parcours saved = new Parcours(java.util.UUID.randomUUID(), "tenant_dev", "p", "P",
                new ParcoursDefinition(List.of(), List.of(DossierStatus.OUVERT), null, List.of()), 2, "agent-1", "agent-1",
                Instant.EPOCH, Instant.EPOCH, null, null);
        when(manage.save(any(), any())).thenReturn(saved);
        tools.defineParcours("p", "P", STATUSES, List.of("OUVERT"), "", STEPS, "PERMIS", 1, null);
        ArgumentCaptor<ManageParcoursUseCase.SaveCommand> command = ArgumentCaptor.forClass(ManageParcoursUseCase.SaveCommand.class);
        verify(manage).save(any(), command.capture());
        assertThat(command.getValue().expectedVersion()).isEqualTo(1);
        assertThat(command.getValue().definition().onStartDossierStatus()).isNull();
        assertThat(command.getValue().definition().steps()).hasSize(2);
        assertThat(command.getValue().definition().dossierTypeCode()).isEqualTo("PERMIS");
    }

    @Test
    void aHostileStepArgumentIsRefusedBeforeAnythingIsSaved() {
        List<Map<String, Object>> hostile = List.of(Map.of("id", "a", "kind", "DECISION", "params", Map.of(), "defaultNext", "a",
                "branches", List.of(Map.of("variable", "x", "operator", "EQ", "value", "&apos; || true || &apos;", "next", "a"))));
        assertThatThrownBy(() -> tools.defineParcours("p", "P", STATUSES, List.of("OUVERT"), null, hostile, null, null, null))
                .hasRootCauseInstanceOf(InvalidParcoursException.class);
        verify(manage, never()).save(any(), any());
    }

    @Test
    void compileWritesADraftWithTheCallerProvenanceAndNeverPublishes() {
        when(compile.compile(any(), any(), eq("p"), anyBoolean(), anyBoolean())).thenReturn(
                new CompileParcoursUseCase.Result(compiled(), new be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase.Proposal(
                        be.myrmacta.domain.workflow.model.DraftArtifactType.BPMN, "p", Instant.EPOCH, false)));
        Map<String, Object> out = tools.compileParcours(" p ", false, true);
        assertThat(out).containsEntry("status", "DRAFT_PENDING").containsEntry("dryRun", false).containsKey("xml");
        ArgumentCaptor<Provenance> provenance = ArgumentCaptor.forClass(Provenance.class);
        verify(compile).compile(any(), provenance.capture(), eq("p"), eq(false), eq(true));
        assertThat(provenance.getValue()).isEqualTo(Provenance.MCP);
    }
}
