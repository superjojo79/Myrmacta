package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §113 (D-MAILROOM-THREAD) — sans fil sûr, le courrier suit le routage ordinaire, sans aucune écriture sur un autre dossier. */
class AttachToThreadDelegateTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

    private final MailMessageIndex index = mock(MailMessageIndex.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierRelationRepository relations = mock(DossierRelationRepository.class);
    private final CreateDossierRelationUseCase createRelation = mock(CreateDossierRelationUseCase.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AttachInboundFilesUseCase attachFiles = mock(AttachInboundFilesUseCase.class);
    private final DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
    private final WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
    private final SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
    private final WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object> written = new HashMap<>();
    private final UUID mailId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private AttachToThreadDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new AttachToThreadDelegate(index, dossiers, relations, createRelation, elements, storage, attachFiles, links,
                participants, notifications, engine, new TenantProperties(TENANT));
        variables.put("dossierId", mailId.toString());
        variables.put("mailFrom", "compta@fournisseur.example");
        variables.put("mailInReplyTo", "<sent-1@myrmacta.example>");
        variables.put("mailSenderAuthenticated", true);
        when(execution.getVariable(anyString())).thenAnswer(call -> variables.get(call.<String>getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> written.put(call.getArgument(0), call.getArgument(1)))
                .when(execution).setVariable(anyString(), any());
        when(dossiers.findById(mailId)).thenReturn(Optional.of(dossier(mailId, "DOS-COURRIER-1", DossierStatus.EN_COURS)));
        when(index.find(TENANT, MailIndexEntry.Kind.MESSAGE_ID, "<sent-1@myrmacta.example>")).thenReturn(Optional.of(
                new MailIndexEntry(TENANT, MailIndexEntry.Kind.MESSAGE_ID, "<sent-1@myrmacta.example>", MailIndexEntry.Direction.OUT,
                        threadId, "compta@fournisseur.example", NOW)));
    }

    @Test
    void aMailOutsideAnyKnownThreadIsNew() {
        variables.remove("mailInReplyTo");

        delegate.execute(execution);

        assertNotThreaded("hors fil");
    }

    @Test
    void anUnattestedSenderNeverJoinsTheThread() {
        variables.put("mailSenderAuthenticated", false);

        delegate.execute(execution);

        assertNotThreaded("expéditeur non attesté");
    }

    @Test
    void aClosedThreadDossierIsNotReopenedByAReply() {
        when(dossiers.findById(threadId)).thenReturn(Optional.of(dossier(threadId, "DOS-FACTURE-7", DossierStatus.CLOTURE)));

        delegate.execute(execution);

        assertNotThreaded("dossier du fil clôturé : DOS-FACTURE-7");
    }

    private void assertNotThreaded(String reason) {
        assertThat(written).containsEntry("filResultat", "NOUVEAU").containsEntry("filMotif", reason);
        verifyNoInteractions(createRelation, attachFiles, notifications, engine, storage);
    }

    private static Dossier dossier(UUID id, String reference, DossierStatus status) {
        return new Dossier(id, TENANT, reference, "COURRIER", "Courrier", status, Map.of(), null, 0L, "trigger", NOW, "trigger", NOW,
                null, null, null);
    }
}
