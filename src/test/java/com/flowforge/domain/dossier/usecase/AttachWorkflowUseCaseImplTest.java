package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachWorkflowUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String PROCESS = "smokeProcess";

    private DossierWorkflowLinkRepository linkRepository;
    private DossierRepository dossierRepository;
    private WorkflowTypeConfigRepository configRepository;
    private AttachWorkflowUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        dossierRepository = mock(DossierRepository.class);
        configRepository = mock(WorkflowTypeConfigRepository.class);
        useCase = new AttachWorkflowUseCaseImpl(linkRepository, dossierRepository, configRepository);
    }

    @Test
    void reserveOrResume_returnsExistingReservedLinkWithoutCreatingAnother() {
        UUID dossierId = UUID.randomUUID();
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(linkRepository.findReservedByDossierAndProcess(dossierId, PROCESS))
                .thenReturn(Optional.of(reserved));

        DossierWorkflowLink result = useCase.reserveOrResume(dossierId, PROCESS, "outbox:1");

        assertThat(result).isEqualTo(reserved);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void reserveOrResume_returnsActiveLinkForSameProcessWithoutSecondReserve() {
        UUID dossierId = UUID.randomUUID();
        DossierWorkflowLink active = reservedLink(dossierId).withProcessInstance("proc-1");
        when(linkRepository.findReservedByDossierAndProcess(dossierId, PROCESS))
                .thenReturn(Optional.empty());
        when(linkRepository.findActiveByDossierId(dossierId)).thenReturn(Optional.of(active));

        DossierWorkflowLink result = useCase.reserveOrResume(dossierId, PROCESS, "outbox:replay");

        assertThat(result.isActive()).isTrue();
        assertThat(result.processInstanceId()).isEqualTo("proc-1");
        verify(linkRepository, never()).save(any());
    }

    @Test
    void reserveOrResume_throwsWhenAnotherWorkflowIsActive() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId, DossierStatus.OUVERT);
        DossierWorkflowLink otherActive = reservedLink(dossierId)
                .withProcessInstance("proc-other");
        // different process key on active link
        otherActive = new DossierWorkflowLink(
                otherActive.id(),
                dossierId,
                TENANT,
                "proc-other",
                "otherProcess",
                1,
                "STARTED",
                "manual",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null);

        when(linkRepository.findReservedByDossierAndProcess(dossierId, PROCESS))
                .thenReturn(Optional.empty());
        when(linkRepository.findActiveByDossierId(dossierId)).thenReturn(Optional.of(otherActive));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config()));

        assertThatThrownBy(() -> useCase.reserveOrResume(dossierId, PROCESS, "outbox:collision"))
                .isInstanceOf(WorkflowAlreadyRunningException.class);
    }

    private static WorkflowTypeConfig config() {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                TENANT,
                PROCESS,
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("COMPLETED", "Done", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS),
                null);
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
                "manual",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null);
    }

    private static Dossier dossier(UUID id, DossierStatus status) {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        return new Dossier(
                id,
                TENANT,
                "DOS-1",
                "WF",
                "Label",
                status,
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
