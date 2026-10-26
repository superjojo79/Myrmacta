package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.precheck.model.FindingKind;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckOutcome;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.in.ManageDossierPrechecksUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import be.myrmacta.infrastructure.mcp.McpActorScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** TODO §106 (lot B, B6) — outils MCP « lancer » et « lire » une pré-vérification, au nom de l'acteur. */
class DossierPrecheckToolsTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final PrecheckSpec SPEC = PrecheckSpec.parse(Map.of(PrecheckSpec.QUESTIONS, "q = Question ?"), "check");

    private final ManageDossierPrechecksUseCase useCase = mock(ManageDossierPrechecksUseCase.class);
    private final DossierPrecheckTools tools = new DossierPrecheckTools(useCase, new McpAccessContextFactory());
    private final AccessContext.User agent = AccessContext.user("agent", Set.of("DOSSIER_MANAGER"));
    private final UUID dossierId = UUID.randomUUID();

    @Test
    void runQueuesARerunInTheActorsName() {
        PrecheckRun pending = PrecheckRun.pending(dossierId, PrecheckRun.Trigger.MCP, "agent", null, SPEC, NOW);
        when(useCase.request(dossierId, agent, PrecheckRun.Trigger.MCP)).thenReturn(pending);

        Map<String, Object> result = McpActorScope.call(agent, () -> tools.runDossierPrecheck(dossierId.toString()));

        assertThat(result).containsEntry("runId", pending.id().toString()).containsEntry("status", "PENDING");
        verify(useCase).request(eq(dossierId), eq(agent), eq(PrecheckRun.Trigger.MCP));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getReturnsTheThreeLatestRunsWithTheirFindings() {
        PrecheckOutcome outcome = new PrecheckOutcome(PrecheckRun.Status.SUCCEEDED, null, "p", "m", "v1", "h", "Synthèse",
                1, 1, 0, List.of(new PrecheckFinding(UUID.randomUUID(), 0, FindingKind.TO_CHECK,
                        PrecheckFinding.Origin.MODEL, PrecheckFinding.SourceType.QUESTION, "q", "À voir.", 0.4, null, null,
                        null)));
        List<PrecheckRun> runs = IntStream.range(0, 5).mapToObj(i ->
                PrecheckRun.pending(dossierId, PrecheckRun.Trigger.WORKFLOW, "system", null, SPEC, NOW).completed(outcome, NOW))
                .toList();
        when(useCase.list(eq(dossierId), any())).thenReturn(runs);

        Map<String, Object> result = McpActorScope.call(agent, () -> tools.getDossierPrecheck(dossierId.toString()));

        List<Map<String, Object>> prechecks = (List<Map<String, Object>>) result.get("prechecks");
        assertThat(prechecks).hasSize(3);
        assertThat(prechecks.getFirst()).containsEntry("status", "SUCCEEDED").containsEntry("summary", "Synthèse")
                .containsEntry("summaryUntrusted", true);
        assertThat((List<Map<String, Object>>) prechecks.getFirst().get("findings")).singleElement()
                .satisfies(f -> assertThat(f).containsEntry("origin", "MODEL").containsEntry("confidence", 0.4)
                        .containsEntry("decision", null).containsEntry("untrusted", true));
        // F7 : le texte du modèle est signalé comme donnée non fiable pour un client MCP (injection indirecte).
        assertThat(result).containsEntry("notice", DossierPrecheckTools.UNTRUSTED_NOTICE);
    }

    @Test
    void aMalformedDossierIdIsRefused() {
        assertThatThrownBy(() -> McpActorScope.call(agent, () -> tools.runDossierPrecheck("pas-un-uuid")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
