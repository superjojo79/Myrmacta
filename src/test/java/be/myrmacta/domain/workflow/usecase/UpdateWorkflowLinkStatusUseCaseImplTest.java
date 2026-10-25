package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateWorkflowLinkStatusUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String PROCESS = "smokeProcess";

    private DossierWorkflowLinkRepository linkRepository;
    private DossierRepository dossierRepository;
    private WorkflowTypeConfigRepository configRepository;
    private UpdateWorkflowLinkStatusUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        dossierRepository = mock(DossierRepository.class);
        configRepository = mock(WorkflowTypeConfigRepository.class);
        useCase = new UpdateWorkflowLinkStatusUseCaseImpl(linkRepository, dossierRepository, configRepository);
    }

    @Test
    void update_rejectsUnknownStatusForWorkflowKey() {
        UUID linkId = UUID.randomUUID();
        DossierWorkflowLink link = activeLink(linkId, UUID.randomUUID());
        when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config()));

        assertThatThrownBy(() -> useCase.update(linkId, "UNKNOWN", "activity"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown workflow status");
        verify(linkRepository, never()).save(any());
    }

    @Test
    void update_withNoneOutcomeKeepsLinkActiveAndDoesNotClearDossier() {
        UUID dossierId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        DossierWorkflowLink link = activeLink(linkId, dossierId);
        when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config()));
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.update(linkId, "IN_PROGRESS", "task");

        ArgumentCaptor<DossierWorkflowLink> saved = ArgumentCaptor.forClass(DossierWorkflowLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getValue().endedAt()).isNull();
        assertThat(saved.getValue().isActive()).isTrue();
        verify(dossierRepository, never()).save(any());
    }

    private static WorkflowTypeConfig config() {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                TENANT,
                PROCESS,
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("IN_PROGRESS", "In progress", false, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("COMPLETED", "Done", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT),
                null,
                List.of());
    }

    private static DossierWorkflowLink activeLink(UUID linkId, UUID dossierId) {
        return new DossierWorkflowLink(
                linkId,
                dossierId,
                TENANT,
                "proc-1",
                PROCESS,
                1,
                "STARTED",
                "manual",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null,
                null);
    }
}
