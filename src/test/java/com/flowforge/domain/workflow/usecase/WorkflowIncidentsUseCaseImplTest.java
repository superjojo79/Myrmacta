package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.workflow.model.IncidentNotRetryableException;
import com.flowforge.domain.workflow.model.IncidentState;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import com.flowforge.domain.workflow.model.WorkflowIncidentNotFoundException;
import com.flowforge.domain.workflow.port.out.WorkflowIncidentPort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §94 — échecs des jobs : droits, message réservé aux administrateurs, rejeu, liste du tenant. */
class WorkflowIncidentsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ADMIN = AccessContext.user("admin", Set.of("WORKFLOW_ADMIN"));
    private static final AccessContext.User PARTICIPANT = AccessContext.user("p1", Set.of("DOSSIER_MANAGER"));
    private static final AccessContext.User EDITOR = AccessContext.user("e1", Set.of("DOSSIER_MANAGER"));
    private static final AccessContext.User STRANGER = AccessContext.user("x1", Set.of("DOSSIER_VIEWER"));

    private DossierWorkflowLinkRepository links;
    private DossierRepository dossiers;
    private WorkflowParticipantPort participants;
    private DossierAccessPolicy accessPolicy;
    private WorkflowIncidentPort incidents;
    private WorkflowIncidentsUseCaseImpl useCase;
    private DossierWorkflowLink link;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        links = mock(DossierWorkflowLinkRepository.class);
        dossiers = mock(DossierRepository.class);
        participants = mock(WorkflowParticipantPort.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        incidents = mock(WorkflowIncidentPort.class);
        useCase = new WorkflowIncidentsUseCaseImpl(TENANT, links, dossiers, participants, accessPolicy, incidents);
        dossier = dossier(UUID.randomUUID(), "DOS-7");
        link = link(dossier.id(), "pi-1");
        when(links.findById(link.id())).thenReturn(Optional.of(link));
        when(dossiers.findById(dossier.id())).thenReturn(Optional.of(dossier));
        when(participants.findParticipantUserIds(link)).thenReturn(List.of("p1"));
        when(accessPolicy.canEdit(dossier, EDITOR)).thenReturn(true);
    }

    private static WorkflowIncident incident(String jobId, String processInstanceId, IncidentState state) {
        return new WorkflowIncident(jobId, processInstanceId, "mailRefus", "E-mail : refus", state,
                Instant.parse("2026-10-18T18:51:26Z"), state == IncidentState.INCIDENT ? 0 : 2,
                "notificationTo source dossierField:email is not a single e-mail address");
    }

    @Test
    void participantsEditorsAndAdminsSeeTheFailuresButOnlyAdminsSeeTheMessage() {
        when(incidents.listByProcessInstance("pi-1", TENANT)).thenReturn(List.of(incident("j1", "pi-1", IncidentState.INCIDENT)));

        assertThat(useCase.listForLink(link.id(), ADMIN)).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("single e-mail address"));
        assertThat(useCase.listForLink(link.id(), PARTICIPANT)).singleElement()
                .satisfies(i -> assertThat(i.message()).isNull());
        assertThat(useCase.listForLink(link.id(), EDITOR)).singleElement()
                .satisfies(i -> {
                    assertThat(i.message()).isNull();
                    assertThat(i.state()).isEqualTo(IncidentState.INCIDENT);
                });
        assertThatThrownBy(() -> useCase.listForLink(link.id(), STRANGER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aMereReaderOfTheDossierGetsAnEmptyListInsteadOfADenial() {
        when(accessPolicy.canViewType(TENANT, dossier.dossierTypeCode(), STRANGER)).thenReturn(true);
        when(incidents.listByProcessInstance("pi-1", TENANT)).thenReturn(List.of(incident("j1", "pi-1", IncidentState.INCIDENT)));

        assertThat(useCase.listForLink(link.id(), STRANGER)).isEmpty();
    }

    @Test
    void aReservedLinkWithoutProcessInstanceHasNoFailure() {
        DossierWorkflowLink reserved = link(dossier.id(), null);
        when(links.findById(reserved.id())).thenReturn(Optional.of(reserved));
        when(participants.findParticipantUserIds(reserved)).thenReturn(List.of("p1"));

        assertThat(useCase.listForLink(reserved.id(), ADMIN)).isEmpty();
    }

    @Test
    void anAdminRetriesAnIncidentOfThisExecutionWithThreeAttempts() {
        when(incidents.find("j1", TENANT)).thenReturn(Optional.of(incident("j1", "pi-1", IncidentState.INCIDENT)));

        useCase.retry(link.id(), "j1", ADMIN);

        verify(incidents).retry("j1", TENANT, WorkflowIncidentsUseCaseImpl.RETRIES_ON_RETRY);
        assertThat(WorkflowIncidentsUseCaseImpl.RETRIES_ON_RETRY).isEqualTo(3);
    }

    @Test
    void retryIsRefusedToNonAdminsUnknownJobsJobsOfAnotherExecutionAndJobsStillRetrying() {
        when(incidents.find("j1", TENANT)).thenReturn(Optional.of(incident("j1", "pi-1", IncidentState.INCIDENT)));
        when(incidents.find("other", TENANT)).thenReturn(Optional.of(incident("other", "pi-2", IncidentState.INCIDENT)));
        when(incidents.find("busy", TENANT)).thenReturn(Optional.of(incident("busy", "pi-1", IncidentState.RETRYING)));
        when(incidents.find("absent", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.retry(link.id(), "j1", EDITOR)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.retry(link.id(), "absent", ADMIN))
                .isInstanceOf(WorkflowIncidentNotFoundException.class);
        assertThatThrownBy(() -> useCase.retry(link.id(), "other", ADMIN))
                .isInstanceOf(WorkflowIncidentNotFoundException.class);
        assertThatThrownBy(() -> useCase.retry(link.id(), "busy", ADMIN))
                .isInstanceOf(IncidentNotRetryableException.class);
        assertThatThrownBy(() -> useCase.retry(link.id(), " ", ADMIN)).isInstanceOf(IllegalArgumentException.class);
        verify(incidents, never()).retry(anyString(), anyString(), anyInt());
    }

    @Test
    void theTenantListIsForAdminsLinksEachFailureToItsDossierAndSkipsOrphans() {
        when(incidents.listByTenant(TENANT, WorkflowIncidentsUseCaseImpl.TENANT_SCAN_LIMIT + 1)).thenReturn(List.of(
                incident("j1", "pi-1", IncidentState.INCIDENT),
                incident("j2", "pi-orphan", IncidentState.RETRYING)));
        when(links.findByProcessInstanceIds(TENANT, List.of("pi-1", "pi-orphan"))).thenReturn(List.of(link));

        var result = useCase.listForTenant(ADMIN, new PageQuery(0, 20));

        assertThat(result.truncated()).isFalse();
        assertThat(result.page().totalElements()).isEqualTo(1);
        assertThat(result.page().content()).singleElement().satisfies(t -> {
            assertThat(t.dossierReference()).isEqualTo("DOS-7");
            assertThat(t.linkId()).isEqualTo(link.id());
            assertThat(t.processDefinitionKey()).isEqualTo("autorisationRiverain");
            assertThat(t.incident().message()).isNotNull();
        });
        assertThatThrownBy(() -> useCase.listForTenant(EDITOR, new PageQuery(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theTenantListPaginatesAndSaysWhenTheScanLimitWasReallyExceeded() {
        int limit = WorkflowIncidentsUseCaseImpl.TENANT_SCAN_LIMIT;
        List<WorkflowIncident> exactly = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            exactly.add(incident("j" + i, "pi-1", IncidentState.INCIDENT));
        }
        List<WorkflowIncident> beyond = new ArrayList<>(exactly);
        beyond.add(incident("j-extra", "pi-1", IncidentState.INCIDENT));
        when(links.findByProcessInstanceIds(TENANT, List.of("pi-1"))).thenReturn(List.of(link));

        when(incidents.listByTenant(TENANT, limit + 1)).thenReturn(exactly);
        var atTheLimit = useCase.listForTenant(ADMIN, new PageQuery(2, 20));
        assertThat(atTheLimit.truncated()).isFalse();
        assertThat(atTheLimit.page().content()).hasSize(20);
        assertThat(atTheLimit.page().content().getFirst().incident().jobId()).isEqualTo("j40");
        assertThat(atTheLimit.page().totalElements()).isEqualTo(limit);

        when(incidents.listByTenant(TENANT, limit + 1)).thenReturn(beyond);
        var overTheLimit = useCase.listForTenant(ADMIN, new PageQuery(0, 20));
        assertThat(overTheLimit.truncated()).isTrue();
        assertThat(overTheLimit.page().totalElements()).isEqualTo(limit);
    }

    @Test
    void theSystemDossierViewCoversEveryExecutionAndNeverCarriesTheMessage() {
        DossierWorkflowLink previous = link(dossier.id(), "pi-0");
        DossierWorkflowLink reserved = link(dossier.id(), null);
        when(links.findByDossierIdOrderBySequence(dossier.id())).thenReturn(List.of(previous, link, reserved));
        when(incidents.listByProcessInstance("pi-0", TENANT)).thenReturn(List.of(incident("j0", "pi-0", IncidentState.RETRYING)));
        when(incidents.listByProcessInstance("pi-1", TENANT)).thenReturn(List.of(incident("j1", "pi-1", IncidentState.INCIDENT)));

        assertThat(useCase.listForDossierSystem(dossier.id()))
                .extracting(t -> t.incident().jobId(), t -> t.incident().message(), t -> t.linkId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("j0", null, previous.id()),
                        org.assertj.core.groups.Tuple.tuple("j1", null, link.id()));
        // Une exécution réservée (sans instance de process) n'est pas interrogée.
        verify(incidents, never()).listByProcessInstance(org.mockito.ArgumentMatchers.isNull(), any());
    }

    private static DossierWorkflowLink link(UUID dossierId, String processInstanceId) {
        return new DossierWorkflowLink(UUID.randomUUID(), dossierId, TENANT, processInstanceId, "autorisationRiverain", 1,
                "EN_INSTRUCTION", "manual:idem", Instant.parse("2026-10-18T18:49:26Z"), null, null, "p1");
    }

    private static Dossier dossier(UUID id, String reference) {
        Instant now = Instant.parse("2026-10-18T18:49:00Z");
        return new Dossier(id, TENANT, reference, "AUTORISATION_RIVERAIN", "Recette", DossierStatus.EN_COURS, Map.of(),
                null, 0, "p1", now, "p1", now, null, null, null);
    }
}
