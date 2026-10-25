package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.dossier.ChildDossierRelationConfirmer;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateChildDossierDelegateTest {

    private ChildDossierRelationConfirmer confirmer;
    private ManualStartOrchestrator orchestrator;
    private DossierRepository dossierRepository;
    private UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private CreateChildDossierDelegate delegate;
    private DelegateExecution execution;
    private UUID sourceId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        confirmer = mock(ChildDossierRelationConfirmer.class);
        orchestrator = mock(ManualStartOrchestrator.class);
        dossierRepository = mock(DossierRepository.class);
        updateDossierStatusUseCase = mock(UpdateDossierStatusUseCase.class);
        delegate = new CreateChildDossierDelegate(
                confirmer,
                orchestrator,
                dossierRepository,
                updateDossierStatusUseCase,
                new TenantProperties("tenant_dev"));
        execution = mock(DelegateExecution.class);
        sourceId = UUID.randomUUID();
        childId = UUID.randomUUID();
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        when(execution.getCurrentActivityId()).thenReturn("createChild");
        when(execution.getVariable("dossierId")).thenReturn(sourceId.toString());
        when(execution.getVariable("childDossierTypeCode")).thenReturn("CHILD");
        when(execution.getVariable("childDossierLabel")).thenReturn("Enfant");
        when(execution.getVariable("workflowProcessKey")).thenReturn("smokeProcess");
        when(execution.getVariable("relationType")).thenReturn("PARENT_CHILD");
        when(execution.getVariable("childDossierFields")).thenReturn(Map.of("k", "v"));
        when(execution.getVariable("childWorkflowVariables")).thenReturn(Map.of("priority", "HIGH"));
        when(confirmer.confirm(
                        eq("tenant_dev"),
                        eq(sourceId),
                        eq("CHILD"),
                        eq("Enfant"),
                        eq(Map.of("k", "v")),
                        eq(RelationType.PARENT_CHILD),
                        eq("pi-1:createChild")))
                .thenReturn(childId);
        when(dossierRepository.findById(childId)).thenReturn(Optional.of(child(DossierStatus.DRAFT)));
    }

    @Test
    void execute_confirmsThenStartsWorkflowWithDerivedKey() {
        delegate.execute(execution);

        verify(updateDossierStatusUseCase).update(any(), any());
        verify(orchestrator).start(childId, "smokeProcess", Map.of("priority", "HIGH"), "pi-1:createChild", "workflow");
    }

    @Test
    void execute_rejectsMissingRequiredVariable() {
        when(execution.getVariable("childDossierTypeCode")).thenReturn(null);

        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("childDossierTypeCode");
        verify(confirmer, never()).confirm(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_rejectsUnknownRelationTypeAndMissingActivityId() {
        when(execution.getVariable("relationType")).thenReturn("NOT_A_TYPE");
        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid relationType");

        when(execution.getVariable("relationType")).thenReturn("PARENT_CHILD");
        when(execution.getCurrentActivityId()).thenReturn(" ");
        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
        verify(confirmer, never()).confirm(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_rejectsManualOnlyRelationType() {
        when(execution.getVariable("relationType")).thenReturn("LINKED");

        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PARENT_CHILD");
        verify(confirmer, never()).confirm(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_retriesConfirmOnceWhenLosingARaceThenSucceeds() {
        // The confirmer's own transaction can lose a genuine race on the unique idempotency
        // constraint and must not attempt recovery internally (see ChildDossierRelationConfirmer's
        // comment) — the delegate is responsible for retrying with a fresh confirm() call/transaction.
        when(confirmer.confirm(
                        eq("tenant_dev"),
                        eq(sourceId),
                        eq("CHILD"),
                        eq("Enfant"),
                        eq(Map.of("k", "v")),
                        eq(RelationType.PARENT_CHILD),
                        eq("pi-1:createChild")))
                .thenThrow(new DuplicateDossierRelationException("race"))
                .thenReturn(childId);

        delegate.execute(execution);

        verify(confirmer, times(2)).confirm(any(), any(), any(), any(), any(), any(), any());
        verify(orchestrator).start(childId, "smokeProcess", Map.of("priority", "HIGH"), "pi-1:createChild", "workflow");
    }

    @Test
    void execute_secondRaceLossPropagates() {
        when(confirmer.confirm(
                        eq("tenant_dev"),
                        eq(sourceId),
                        eq("CHILD"),
                        eq("Enfant"),
                        eq(Map.of("k", "v")),
                        eq(RelationType.PARENT_CHILD),
                        eq("pi-1:createChild")))
                .thenThrow(new DuplicateDossierRelationException("race 1"))
                .thenThrow(new DuplicateDossierRelationException("race 2"));

        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(DuplicateDossierRelationException.class);
        verify(confirmer, times(2)).confirm(any(), any(), any(), any(), any(), any(), any());
        verify(orchestrator, never()).start(any(), any(), any(), any(), any());
    }

    @Test
    void execute_replaysStartWithoutOpeningAlreadyOpenChild() {
        when(dossierRepository.findById(childId)).thenReturn(Optional.of(child(DossierStatus.OUVERT)));

        delegate.execute(execution);

        verify(updateDossierStatusUseCase, never()).update(any(), any());
        verify(orchestrator).start(childId, "smokeProcess", Map.of("priority", "HIGH"), "pi-1:createChild", "workflow");
    }

    private Dossier child(DossierStatus status) {
        Instant now = Instant.parse("2026-08-15T08:00:00Z");
        return new Dossier(
                childId,
                "tenant_dev",
                "DOS-CHILD",
                "CHILD",
                "Enfant",
                status,
                Map.of(),
                null,
                0,
                "workflow",
                now,
                "workflow",
                now,
                null,
                null,
                null);
    }
}
