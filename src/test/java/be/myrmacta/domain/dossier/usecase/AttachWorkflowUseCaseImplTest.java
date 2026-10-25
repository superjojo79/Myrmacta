package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.WorkflowAlreadyRunningException;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
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

        DossierWorkflowLink result = useCase.reserveOrResume(dossierId, PROCESS, "outbox:1", "new-starter");

        assertThat(result).isEqualTo(reserved);
        assertThat(result.startedBy()).isEqualTo("original-starter");
        verify(linkRepository, never()).save(any());
    }

    @Test
    void reserveOrResume_returnsActiveLinkForSameProcessWithoutSecondReserve() {
        UUID dossierId = UUID.randomUUID();
        DossierWorkflowLink active = reservedLink(dossierId).withProcessInstance("proc-1");
        when(linkRepository.findReservedByDossierAndProcess(dossierId, PROCESS))
                .thenReturn(Optional.empty());
        when(linkRepository.findActiveByDossierId(dossierId)).thenReturn(Optional.of(active));

        DossierWorkflowLink result = useCase.reserveOrResume(dossierId, PROCESS, "outbox:replay", "new-starter");

        assertThat(result.isActive()).isTrue();
        assertThat(result.processInstanceId()).isEqualTo("proc-1");
        assertThat(result.startedBy()).isEqualTo("original-starter");
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
                null,
                null);

        when(linkRepository.findReservedByDossierAndProcess(dossierId, PROCESS))
                .thenReturn(Optional.empty());
        when(linkRepository.findActiveByDossierId(dossierId)).thenReturn(Optional.of(otherActive));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config()));

        assertThatThrownBy(() -> useCase.reserveOrResume(dossierId, PROCESS, "outbox:collision", "starter-sub"))
                .isInstanceOf(WorkflowAlreadyRunningException.class);
    }

    @Test
    void reserve_persistsStartedByOnNewLink() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId, DossierStatus.OUVERT);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config()));
        when(linkRepository.findActiveByDossierId(dossierId)).thenReturn(Optional.empty());
        when(linkRepository.nextSequence(dossierId)).thenReturn(7);
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DossierWorkflowLink reserved = useCase.reserve(dossierId, PROCESS, "manual:idem", "starter-sub");

        assertThat(reserved.startedBy()).isEqualTo("starter-sub");
    }

    @Test
    void getHistory_delegatesToPagedRepositoryMethod() {
        UUID dossierId = UUID.randomUUID();
        PageQuery query = PageQuery.of(0, 20);
        DossierWorkflowLink link = reservedLink(dossierId);
        when(linkRepository.pageByDossierId(dossierId, query))
                .thenReturn(PageResult.of(List.of(link), 0, 20, 1));

        PageResult<DossierWorkflowLink> page = useCase.getHistory(dossierId, query);

        assertThat(page.content()).containsExactly(link);
        assertThat(page.totalElements()).isEqualTo(1);
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
                null,
                List.of());
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
                null,
                "original-starter");
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
                null,
                null);
    }
}
