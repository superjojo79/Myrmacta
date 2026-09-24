package com.flowforge.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.portal.PortalParticipantNotifier;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.port.out.PortalAvailabilityPort;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTaskLookupPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §68 — cas limites de la gestion instructeur (Phase 8). */
class ManagePortalLinkUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private final InMemoryPortalLinkRepository repository = new InMemoryPortalLinkRepository();
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierAccessPolicy policy = mock(DossierAccessPolicy.class);
    private final PortalTaskLookupPort lookup = mock(PortalTaskLookupPort.class);
    private final WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
    private final PortalAvailabilityPort availability = mock(PortalAvailabilityPort.class);
    private final AccessContext.User admin = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    private final UUID dossierId = UUID.randomUUID();
    private ManagePortalLinkUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        IssuePortalLinkUseCaseImpl issuer = new IssuePortalLinkUseCaseImpl("tenant_dev", repository,
                mock(TaskQuestionSchemaRepository.class), mock(PortalRecipientResolverPort.class),
                mock(PortalParticipantNotifier.class), Clock.fixed(NOW, ZoneOffset.UTC), 7, 30);
        useCase = new ManagePortalLinkUseCaseImpl("tenant_dev", repository, dossiers, policy, lookup, engine, issuer,
                availability, Clock.fixed(NOW, ZoneOffset.UTC));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(new Dossier(dossierId, "tenant_dev", "REF", "T", "L",
                DossierStatus.OUVERT, Map.of(), null, 0L, "u", NOW, null, null, null, null, null)));
        when(availability.isPortalEnabled()).thenReturn(true);
    }

    @Test
    void unknownOrForeignTenantDossier_isNotFound() {
        UUID other = UUID.randomUUID();
        when(dossiers.findById(other)).thenReturn(Optional.of(new Dossier(other, "tenant_b", "REF", "T", "L",
                DossierStatus.OUVERT, Map.of(), null, 0L, "u", NOW, null, null, null, null, null)));

        assertThatThrownBy(() -> useCase.list(other, admin)).isInstanceOf(DossierNotFoundException.class);
        assertThatThrownBy(() -> useCase.list(UUID.randomUUID(), admin)).isInstanceOf(DossierNotFoundException.class);
    }

    @Test
    void send_isRefusedWhenThePortalIsDisabled() {
        when(availability.isPortalEnabled()).thenReturn(false);

        assertThatThrownBy(() -> useCase.send(dossierId, "task-1", admin)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void send_forAClosedOrUnknownTask_isAConflict() {
        when(lookup.findOpenTask("task-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.send(dossierId, "task-1", admin)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void send_forAnInternalTask_isRejected() {
        when(lookup.findOpenTask("task-1"))
                .thenReturn(Optional.of(new PortalTaskLookupPort.OpenTask("task-1", "pi-1", "T", null)));
        when(engine.findDossierIdVariable("pi-1")).thenReturn(Optional.of(dossierId.toString()));
        when(lookup.findExternalTaskDefinition("task-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.send(dossierId, "task-1", admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an external portal task");
    }

    @Test
    void revoke_ofALinkFromAnotherDossier_isNotFound_andOfADeadLink_isAConflict() {
        PortalLink foreign = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "t9", "pi",
                "avis", "a@b.org", 7, NOW, "x");
        repository.insert(foreign);
        assertThatThrownBy(() -> useCase.revoke(dossierId, foreign.id(), admin))
                .isInstanceOf(DossierNotFoundException.class);

        PortalLink done = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", dossierId, "t8", "pi", "avis",
                "a@b.org", 7, NOW, "x").activated("h", NOW).completed(NOW);
        repository.insert(done);
        assertThatThrownBy(() -> useCase.revoke(dossierId, done.id(), admin)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void list_reportsWhetherTheActorCanManage() {
        when(policy.canEdit(any(), any())).thenReturn(false);

        assertThat(useCase.list(dossierId, admin).canManage()).isFalse();
    }
}
