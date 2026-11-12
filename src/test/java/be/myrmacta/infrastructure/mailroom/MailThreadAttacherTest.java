package be.myrmacta.infrastructure.mailroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §133 (D-I18N-DOSSIER-TEXT) — l'avertissement de fil suit la langue du tenant. */
class MailThreadAttacherTest {

    @Test
    void anEnglishTenantWarnsParticipantsInEnglish() {
        DossierRelationRepository relations = mock(DossierRelationRepository.class);
        DossierElementRepository elements = mock(DossierElementRepository.class);
        DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
        WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
        SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
        when(relations.findByIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        when(relations.findByDossier(any(), any())).thenReturn(List.of());
        when(elements.findByDossierIdOrderByAddedAtAsc(any())).thenReturn(List.of());
        DossierWorkflowLink link = mock(DossierWorkflowLink.class);
        when(links.findActiveByDossierId(any())).thenReturn(Optional.of(link));
        when(participants.findParticipantUserIds(link)).thenReturn(List.of("alice"));
        MailThreadAttacher attacher = new MailThreadAttacher(relations, mock(CreateDossierRelationUseCase.class), elements,
                mock(DocumentStoragePort.class), mock(AttachInboundFilesUseCase.class), links, participants, notifications,
                mock(WorkflowEnginePort.class), Locale.ENGLISH);
        Instant now = Instant.parse("2026-11-12T12:00:00Z");
        UUID mailId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Dossier mail = dossier(mailId, "DOS-COURRIER-1", now);
        Dossier target = dossier(targetId, "DOS-FACTURE-7", now);

        attacher.attach("tenant_dev", mail, target, "key", "workflow");

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        org.mockito.Mockito.verify(notifications).send(event.capture(), org.mockito.ArgumentMatchers.eq("tenant_dev"));
        assertThat(event.getValue().payload()).containsEntry("title", "New mail")
                .containsEntry("body", "A mail item (DOS-COURRIER-1) replies on the thread of dossier DOS-FACTURE-7.");
    }

    private static Dossier dossier(UUID id, String reference, Instant now) {
        return new Dossier(id, "tenant_dev", reference, "COURRIER", reference, DossierStatus.OUVERT, Map.of(), null, 0L, "workflow",
                now, "workflow", now, null, null, null);
    }
}
