package com.flowforge.infrastructure.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class WorkflowStartConfirmerTest {

    private static final String TENANT = "tenant_dev";
    private static final String PROCESS = "smokeProcess";

    private AttachWorkflowUseCase attachWorkflow;
    private IdempotencyStore idempotencyStore;
    private UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private WorkflowTypeConfigRepository configRepository;
    private WorkflowStartConfirmer confirmer;

    @BeforeEach
    void setUp() {
        attachWorkflow = mock(AttachWorkflowUseCase.class);
        idempotencyStore = mock(IdempotencyStore.class);
        updateDossierStatusUseCase = mock(UpdateDossierStatusUseCase.class);
        configRepository = mock(WorkflowTypeConfigRepository.class);
        confirmer = new WorkflowStartConfirmer(attachWorkflow, idempotencyStore, updateDossierStatusUseCase, configRepository);
    }

    @Test
    void confirm_isAnnotatedTransactional() throws NoSuchMethodException {
        Method method = WorkflowStartConfirmer.class.getMethod(
                "confirm", Dossier.class, DossierWorkflowLink.class, String.class, String.class, String.class);

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void confirm_confirmsStartedAndRemembersIdempotencyKey() {
        Dossier dossier = dossier();
        DossierWorkflowLink reserved = reservedLink(dossier.id());
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config(null)));

        confirmer.confirm(dossier, reserved, "proc-1", PROCESS, "idem-1");

        verify(attachWorkflow).confirmStarted(reserved.id(), "proc-1");
        verify(idempotencyStore).remember(TENANT, dossier.id(), "idem-1", reserved.id());
        verify(updateDossierStatusUseCase, never()).update(any(), any());
    }

    @Test
    void confirm_appliesOnStartDossierStatusWhenConfigured() {
        Dossier dossier = dossier();
        DossierWorkflowLink reserved = reservedLink(dossier.id());
        when(configRepository.findByWorkflowKey(TENANT, PROCESS))
                .thenReturn(Optional.of(config(DossierStatus.EN_COURS)));

        confirmer.confirm(dossier, reserved, "proc-1", PROCESS, "idem-1");

        verify(updateDossierStatusUseCase).update(
                eq(new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                        dossier.id(), DossierStatus.EN_COURS, "workflow", null)),
                eq(AccessContext.system("workflow-start")));
    }

    @Test
    void confirm_throwsWhenConfigMissing() {
        Dossier dossier = dossier();
        DossierWorkflowLink reserved = reservedLink(dossier.id());
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> confirmer.confirm(dossier, reserved, "proc-1", PROCESS, "idem-1"))
                .isInstanceOf(WorkflowTypeConfigNotFoundException.class);
    }

    private static WorkflowTypeConfig config(DossierStatus onStart) {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                TENANT,
                PROCESS,
                "Smoke",
                List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS),
                onStart);
    }

    private static DossierWorkflowLink reservedLink(UUID dossierId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                null,
                PROCESS,
                1,
                "STARTED",
                "manual:idem-1",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null);
    }

    private static Dossier dossier() {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        return new Dossier(
                UUID.randomUUID(),
                TENANT,
                "DOS-1",
                "WF",
                "Label",
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0,
                "u1",
                now,
                "u1",
                now,
                null,
                null);
    }
}
