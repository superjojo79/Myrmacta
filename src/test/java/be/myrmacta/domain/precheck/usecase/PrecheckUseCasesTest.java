package be.myrmacta.domain.precheck.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.precheck.PrecheckEngine;
import be.myrmacta.domain.precheck.model.FindingKind;
import be.myrmacta.domain.precheck.model.PrecheckConflictException;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckDisabledException;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckFinding.SourceType;
import be.myrmacta.domain.precheck.model.PrecheckNotFoundException;
import be.myrmacta.domain.precheck.model.PrecheckOutcome;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.in.RunDossierPrecheckUseCase;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.domain.precheck.port.out.PrecheckVariablesPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §106 (lot B, D-PRECHECK-STORE, D-PRECHECK-QUEUE) — exécution, relance, lecture, décision et export. */
class PrecheckUseCasesTest {

    private static final String TENANT = "t";
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final PrecheckSpec SPEC = PrecheckSpec.parse(
            Map.of(PrecheckSpec.RULES, "variable:doublons > 0 => INCOHERENCE : Doublon."), "check");

    private final DossierRepository dossierRepository = mock(DossierRepository.class);
    private final DossierTypeRepository typeRepository = mock(DossierTypeRepository.class);
    private final DossierElementRepository elementRepository = mock(DossierElementRepository.class);
    private final DossierAccessPolicy accessPolicy = mock(DossierAccessPolicy.class);
    private final PrecheckEngine engine = mock(PrecheckEngine.class);
    private final PrecheckRunRepository runs = mock(PrecheckRunRepository.class);
    private final PrecheckVariablesPort variables = mock(PrecheckVariablesPort.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private boolean enabled = true;
    private long tokensToday = 0;

    private final RunDossierPrecheckUseCaseImpl runner = new RunDossierPrecheckUseCaseImpl(TENANT, dossierRepository,
            typeRepository, elementRepository, engine, runs, variables, clock);
    private final ManageDossierPrechecksUseCaseImpl manage = new ManageDossierPrechecksUseCaseImpl(TENANT,
            dossierRepository, accessPolicy, runs, () -> enabled,
            new be.myrmacta.domain.precheck.PrecheckBudget(1_000, since -> tokensToday, clock), 2, clock);
    private final AccessContext.User agent = AccessContext.user("agent", Set.of("DOSSIER_EDITOR"));

    private Dossier dossier;
    private PrecheckOutcome outcome;

    @BeforeEach
    void setUp() {
        dossier = dossier(DossierStatus.EN_COURS);
        when(dossierRepository.findById(dossier.id())).thenReturn(Optional.of(dossier));
        when(typeRepository.findByCode(TENANT, "RIV")).thenReturn(Optional.of(new DossierType(UUID.randomUUID(), TENANT,
                "RIV", "Riverain", "riverain", true, false, List.of(), null, false, null, "u", "u")));
        when(elementRepository.findByDossierIdOrderByAddedAtAsc(dossier.id())).thenReturn(List.of());
        outcome = new PrecheckOutcome(PrecheckRun.Status.SUCCEEDED, null, "fake", "m", "v1", "h", "Synthèse", 10, 5, 0,
                List.of(PrecheckFinding.deterministic(0, FindingKind.MISSING, SourceType.FIELD, "a", "Manque a."),
                        PrecheckFinding.deterministic(1, FindingKind.INCONSISTENT, SourceType.RULE, "doublons", "Doublon."),
                        new PrecheckFinding(UUID.randomUUID(), 2, FindingKind.MISSING, PrecheckFinding.Origin.MODEL,
                                SourceType.FIELD, "b", "Le modèle croit que b manque.", 0.7, null, null, null)));
        when(engine.compute(any(), any(), any(), any(), anyMap())).thenAnswer(call -> outcome);
        when(runs.insert(any())).thenReturn(true);
        when(runs.complete(any())).thenReturn(true);
    }

    private Dossier dossier(DossierStatus status) {
        return new Dossier(UUID.randomUUID(), TENANT, "RIV-1", "RIV", "Riverain", status, Map.of(), null, 0, "agent", NOW,
                "agent", NOW, null, null, null);
    }

    private PrecheckRun finishedRun() {
        return PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.WORKFLOW, "system", null, SPEC, NOW)
                .completed(outcome, NOW);
    }

    // --- Brique du workflow ------------------------------------------------------------------------------------

    @Test
    void theWorkflowRunIsStoredAndTheResultCountsOnlyDeterministicFindings() {
        UUID link = UUID.randomUUID();
        RunDossierPrecheckUseCase.WorkflowResult result = runner.runInWorkflow(dossier.id(), link, SPEC,
                Map.of("doublons", 1));

        ArgumentCaptor<PrecheckRun> stored = ArgumentCaptor.forClass(PrecheckRun.class);
        verify(runs).insert(stored.capture());
        assertThat(stored.getValue()).satisfies(run -> {
            assertThat(run.trigger()).isEqualTo(PrecheckRun.Trigger.WORKFLOW);
            assertThat(run.requestedBy()).isEqualTo("system");
            assertThat(run.workflowLinkId()).isEqualTo(link);
            assertThat(run.status()).isEqualTo(PrecheckRun.Status.SUCCEEDED);
            assertThat(run.attempts()).isEqualTo(1);
            assertThat(run.finishedAt()).isEqualTo(NOW);
            assertThat(run.findings()).hasSize(3);
        });
        assertThat(result.resultVariable()).containsExactly(
                Map.entry("status", "SUCCEEDED"), Map.entry("missing", 1L), Map.entry("inconsistent", 1L),
                Map.entry("runId", stored.getValue().id().toString()));
        verify(engine).compute(eq(dossier), any(), eq(List.of()), eq(SPEC), eq(Map.of("doublons", 1)));
    }

    @Test
    void aWorkflowRunOnAMissingDossierOrTypeIsAnIncident() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> runner.runInWorkflow(unknown, null, SPEC, Map.of()))
                .isInstanceOf(IllegalStateException.class);

        when(typeRepository.findByCode(TENANT, "RIV")).thenReturn(Optional.empty());
        RunDossierPrecheckUseCase.WorkflowResult result = runner.runInWorkflow(dossier.id(), null, SPEC, Map.of());
        assertThat(result.run().status()).isEqualTo(PrecheckRun.Status.FAILED);
        assertThat(result.run().failure()).isEqualTo("the dossier type no longer exists");
        assertThat(result.resultVariable()).containsEntry("status", "FAILED");
    }

    @Test
    void aDossierOfAnotherTenantIsNotRead() {
        Dossier foreign = new Dossier(UUID.randomUUID(), "other", "X-1", "RIV", "X", DossierStatus.EN_COURS, Map.of(),
                null, 0, "a", NOW, "a", NOW, null, null, null);
        when(dossierRepository.findById(foreign.id())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> runner.runInWorkflow(foreign.id(), null, SPEC, Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> manage.list(foreign.id(), agent)).isInstanceOf(DossierNotFoundException.class);
    }

    // --- File des relances --------------------------------------------------------------------------------------

    @Test
    void aQueuedRunReadsTheCurrentVariables() {
        when(variables.currentVariables(dossier.id())).thenReturn(Map.of("doublons", 0));
        PrecheckRun queued = queued(1);

        PrecheckRun completed = runner.runQueued(queued);

        assertThat(completed.status()).isEqualTo(PrecheckRun.Status.SUCCEEDED);
        assertThat(completed.id()).isEqualTo(queued.id());
        verify(runs).complete(completed);
        verify(engine).compute(eq(dossier), any(), any(), eq(SPEC), eq(Map.of("doublons", 0)));
    }

    /** Revue adversariale de #211, F4 — une tentative dépassée (bail expiré) ne range pas son résultat. */
    @Test
    void aSupersededAttemptDropsItsResult() {
        when(variables.currentVariables(dossier.id())).thenReturn(Map.of());
        when(runs.complete(any())).thenReturn(false);
        PrecheckRun queued = queued(1);
        PrecheckRun result = runner.runQueued(queued);
        assertThat(result).isSameAs(queued);
        assertThat(result.finished()).isFalse();
    }

    @Test
    void unreadableVariablesLeaveRulesUnevaluated() {
        when(variables.currentVariables(dossier.id())).thenThrow(new IllegalStateException("no process"));
        runner.runQueued(queued(1));
        verify(engine).compute(eq(dossier), any(), any(), eq(SPEC), eq(Map.of()));
    }

    @Test
    void aQueuedRunIsAbandonedAfterTwoAttempts() {
        PrecheckRun completed = runner.runQueued(queued(RunDossierPrecheckUseCase.MAX_QUEUE_ATTEMPTS + 1));
        assertThat(completed.status()).isEqualTo(PrecheckRun.Status.FAILED);
        assertThat(completed.failure()).isEqualTo("abandoned after 2 attempts");
        verify(engine, never()).compute(any(), any(), any(), any(), anyMap());
        verify(runs).complete(completed);
    }

    @Test
    void aQueuedRunOnADeletedDossierFails() {
        PrecheckRun run = new PrecheckRun(UUID.randomUUID(), UUID.randomUUID(), PrecheckRun.Trigger.MANUAL, "agent",
                null, SPEC, PrecheckRun.Status.RUNNING, 1, null, null, null, null, null, null, 0, 0, 0, NOW, null, null);
        assertThat(runner.runQueued(run).failure()).isEqualTo("the dossier no longer exists");
    }

    private PrecheckRun queued(int attempts) {
        PrecheckRun pending = PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.MANUAL, "agent", null, SPEC, NOW);
        return new PrecheckRun(pending.id(), pending.dossierId(), pending.trigger(), pending.requestedBy(), null, SPEC,
                PrecheckRun.Status.RUNNING, attempts, null, null, null, null, null, null, 0, 0, 0, NOW, null, null);
    }

    // --- Relance ----------------------------------------------------------------------------------------------

    @Test
    void aRerunReusesTheLatestSpecificationAndIsQueued() {
        when(runs.listByDossier(dossier.id(), 1)).thenReturn(List.of(finishedRun()));

        PrecheckRun run = manage.request(dossier.id(), agent, PrecheckRun.Trigger.MANUAL);

        assertThat(run.status()).isEqualTo(PrecheckRun.Status.PENDING);
        assertThat(run.spec()).isEqualTo(SPEC);
        assertThat(run.requestedBy()).isEqualTo("agent");
        assertThat(run.trigger()).isEqualTo(PrecheckRun.Trigger.MANUAL);
        verify(accessPolicy).assertCanEdit(dossier, agent);
        verify(runs).insert(run);
    }

    @Test
    void aRerunIsRefusedWhenItCannotRun() {
        assertThatThrownBy(() -> manage.request(dossier.id(), agent, PrecheckRun.Trigger.MANUAL))
                .isInstanceOf(PrecheckConflictException.class).hasMessageContaining("no previous pre-check");

        when(runs.listByDossier(dossier.id(), 1)).thenReturn(List.of(finishedRun()));
        when(runs.insert(any())).thenReturn(false);
        assertThatThrownBy(() -> manage.request(dossier.id(), agent, PrecheckRun.Trigger.MCP))
                .isInstanceOf(PrecheckConflictException.class).hasMessageContaining("already queued");

        assertThatThrownBy(() -> manage.request(dossier.id(), agent, PrecheckRun.Trigger.WORKFLOW))
                .isInstanceOf(IllegalArgumentException.class);

        enabled = false;
        assertThatThrownBy(() -> manage.request(dossier.id(), agent, PrecheckRun.Trigger.MANUAL))
                .isInstanceOf(PrecheckDisabledException.class);
    }

    /** Revue adversariale de #211, F3 — les relances ne peuvent pas épuiser le plafond de l'instance. */
    @Test
    void rerunsAreLimitedPerDossierAndKeepAReserveForTheWorkflows() {
        when(runs.listByDossier(dossier.id(), 1)).thenReturn(List.of(finishedRun()));
        when(runs.countRequestsSince(dossier.id(), NOW.minusSeconds(86_400))).thenReturn(2);
        assertThatThrownBy(() -> manage.request(dossier.id(), agent, PrecheckRun.Trigger.MANUAL))
                .isInstanceOf(be.myrmacta.domain.precheck.model.PrecheckRateLimitedException.class)
                .hasMessageContaining("at most 2 pre-check reruns per dossier in 24 hours");

        when(runs.countRequestsSince(dossier.id(), NOW.minusSeconds(86_400))).thenReturn(1);
        tokensToday = 800; // 80 % du plafond : la réserve des workflows est atteinte
        assertThatThrownBy(() -> manage.request(dossier.id(), agent, PrecheckRun.Trigger.MCP))
                .isInstanceOf(be.myrmacta.domain.precheck.model.PrecheckRateLimitedException.class)
                .hasMessageContaining("reruns are suspended");

        tokensToday = 799;
        assertThat(manage.request(dossier.id(), agent, PrecheckRun.Trigger.MANUAL).status())
                .isEqualTo(PrecheckRun.Status.PENDING);
        verify(runs).insert(any());
    }

    @Test
    void aClosedDossierIsNotRerunNorDecided() {
        Dossier closed = dossier(DossierStatus.CLOTURE);
        when(dossierRepository.findById(closed.id())).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> manage.request(closed.id(), agent, PrecheckRun.Trigger.MANUAL))
                .isInstanceOf(PrecheckConflictException.class).hasMessageContaining("closed");
        assertThatThrownBy(() -> manage.decide(closed.id(), UUID.randomUUID(), UUID.randomUUID(),
                PrecheckFinding.Decision.ACCEPTED, agent))
                .isInstanceOf(PrecheckConflictException.class);
    }

    @Test
    void aReaderWithoutEditCannotRerunNorDecide() {
        doThrow(new AccessDeniedException("no EDIT")).when(accessPolicy).assertCanEdit(dossier, agent);
        assertThatThrownBy(() -> manage.request(dossier.id(), agent, PrecheckRun.Trigger.MANUAL))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> manage.decide(dossier.id(), UUID.randomUUID(), UUID.randomUUID(),
                PrecheckFinding.Decision.ACCEPTED, agent))
                .isInstanceOf(AccessDeniedException.class);
        verify(runs, never()).insert(any());
        verify(runs, never()).decide(any(), any(), any(), any(), any());
    }

    // --- Lecture, décision, export --------------------------------------------------------------------------------

    @Test
    void listingNeedsViewRight() {
        when(runs.listByDossier(dossier.id(), 20)).thenReturn(List.of(finishedRun()));
        assertThat(manage.list(dossier.id(), agent)).hasSize(1);
        verify(accessPolicy).assertCanView(dossier, agent);

        doThrow(new AccessDeniedException("no view")).when(accessPolicy).assertCanView(dossier, agent);
        assertThatThrownBy(() -> manage.list(dossier.id(), agent)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aDecisionIsTracedWithWhoAndWhen() {
        PrecheckRun run = finishedRun();
        PrecheckFinding finding = run.findings().get(2);
        PrecheckRun decided = new PrecheckRun(run.id(), run.dossierId(), run.trigger(), run.requestedBy(), null, SPEC,
                run.status(), 1, null, null, null, null, null, null, 0, 0, 0, NOW, NOW,
                List.of(finding.decided(PrecheckFinding.Decision.DISMISSED, "agent", NOW)));
        when(runs.findById(run.id())).thenReturn(Optional.of(run), Optional.of(decided));
        when(runs.decide(run.id(), finding.id(), PrecheckFinding.Decision.DISMISSED, "agent", NOW)).thenReturn(true);

        var decision = manage.decide(dossier.id(), run.id(), finding.id(), PrecheckFinding.Decision.DISMISSED, agent);
        PrecheckFinding result = decision.finding();

        assertThat(decision.previous()).isNull(); // F5 : la décision précédente est rendue pour la trace
        assertThat(result.decision()).isEqualTo(PrecheckFinding.Decision.DISMISSED);
        assertThat(result.decidedBy()).isEqualTo("agent");
        assertThat(result.decidedAt()).isEqualTo(NOW);
    }

    @Test
    void aDecisionOutsideTheDossierOrRunIsNotFound() {
        PrecheckRun run = finishedRun();
        when(runs.findById(run.id())).thenReturn(Optional.of(run));
        UUID foreignFinding = UUID.randomUUID();
        when(runs.decide(any(), eq(foreignFinding), any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> manage.decide(dossier.id(), run.id(), foreignFinding, PrecheckFinding.Decision.ACCEPTED,
                agent)).isInstanceOf(PrecheckNotFoundException.class);

        Dossier other = dossier(DossierStatus.EN_COURS);
        when(dossierRepository.findById(other.id())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> manage.decide(other.id(), run.id(), run.findings().getFirst().id(),
                PrecheckFinding.Decision.ACCEPTED, agent)).isInstanceOf(PrecheckNotFoundException.class);
        assertThatThrownBy(() -> manage.export(other.id(), run.id(), AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(PrecheckNotFoundException.class);
    }

    @Test
    void aRunningRunCannotBeDecided() {
        PrecheckRun pending = PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.MANUAL, "agent", null, SPEC, NOW);
        when(runs.findById(pending.id())).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> manage.decide(dossier.id(), pending.id(), UUID.randomUUID(),
                PrecheckFinding.Decision.ACCEPTED, agent)).isInstanceOf(PrecheckConflictException.class);
    }

    @Test
    void exportNeedsTenantAdmin() {
        PrecheckRun run = finishedRun();
        when(runs.findById(run.id())).thenReturn(Optional.of(run));
        assertThatThrownBy(() -> manage.export(dossier.id(), run.id(), agent)).isInstanceOf(AccessDeniedException.class);
        assertThat(manage.export(dossier.id(), run.id(), AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isEqualTo(run);
    }
}
