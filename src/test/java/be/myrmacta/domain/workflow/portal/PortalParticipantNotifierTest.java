package be.myrmacta.domain.workflow.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

/** TODO §68 US-6 (Phase 8 : contenu des notifications, jamais exercé par les tests moteur sans lien workflow). */
class PortalParticipantNotifierTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private final DossierWorkflowLinkRepository workflowLinks = mock(DossierWorkflowLinkRepository.class);
    private final WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
    private final SendNotificationUseCase send = mock(SendNotificationUseCase.class);
    private final PortalParticipantNotifier notifier =
            new PortalParticipantNotifier("tenant_dev", workflowLinks, participants, send);
    private final UUID dossierId = UUID.randomUUID();
    private DossierWorkflowLink workflowLink;

    @BeforeEach
    void setUp() {
        workflowLink = new DossierWorkflowLink(UUID.randomUUID(), dossierId, "tenant_dev", "pi-1", "portalSmoke", 1,
                "ACTIVE", "manual", NOW, null, null, "agent");
        when(workflowLinks.findByProcessInstanceId("tenant_dev", "pi-1")).thenReturn(Optional.of(workflowLink));
        when(participants.findParticipantUserIds(workflowLink)).thenReturn(List.of("agent", "chef"));
    }

    private PortalLink link(String email) {
        return PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", dossierId, "task-1", "pi-1", "avis", email, 7,
                NOW, "x");
    }

    private List<NotificationEvent> sent(int expected) {
        ArgumentCaptor<NotificationEvent> events = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(send, times(expected)).send(events.capture(), eq("tenant_dev"));
        return events.getAllValues();
    }

    @Test
    void submitted_notifiesEveryParticipantOnBrowser_withMaskedRecipientOnly() {
        notifier.notifySubmitted(link("alice@example.org").activated("h", NOW).completed(NOW));

        List<NotificationEvent> events = sent(2);
        assertThat(events).extracting(NotificationEvent::userId).containsExactlyInAnyOrder("agent", "chef");
        NotificationEvent event = events.getFirst();
        assertThat(event.channels()).containsExactly(NotificationChannel.BROWSER);
        assertThat(event.payload())
                .containsEntry("type", PortalParticipantNotifier.PORTAL_TASK_SUBMITTED)
                .containsEntry("linkId", workflowLink.id().toString())
                .containsEntry("dossierId", dossierId.toString())
                .containsEntry("taskId", "task-1");
        assertThat(event.payload().toString()).contains("a***@example.org").doesNotContain("alice@");
    }

    @Test
    void expired_notifiesWithItsOwnType() {
        notifier.notifyExpired(link("bob@example.org").activated("h", NOW).expired());

        assertThat(sent(2).getFirst().payload()).containsEntry("type", PortalParticipantNotifier.PORTAL_LINK_EXPIRED);
    }

    @ParameterizedTest
    @CsvSource({
            "NO_ADDRESS, aucune adresse",
            "INVALID_ADDRESS, invalide",
            "SCHEMA_MISSING, formulaire",
            "SEND_FAILED, pas pu être envoyé",
            "TASK_GONE, erreur est survenue"
    })
    void notSent_explainsTheReason(PortalLinkStatusReason reason, String expected) {
        PortalLink notSent = PortalLink.notSent(UUID.randomUUID(), "tenant_dev", dossierId, "task-1", "pi-1", "avis",
                null, 7, reason, NOW, "x");

        notifier.notifyNotSent(notSent);

        NotificationEvent event = sent(2).getFirst();
        assertThat(event.payload()).containsEntry("type", PortalParticipantNotifier.PORTAL_LINK_NOT_SENT);
        assertThat(event.payload().get("body").toString()).contains(expected);
    }

    @Test
    void processWithoutWorkflowLink_notifiesNobody() {
        when(workflowLinks.findByProcessInstanceId("tenant_dev", "pi-1")).thenReturn(Optional.empty());

        notifier.notifySubmitted(link("a@b.org").activated("h", NOW).completed(NOW));

        verify(send, never()).send(any(), any());
    }
}
