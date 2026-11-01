package be.myrmacta.domain.connector.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.UnknownConnectorException;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.workflow.model.TriggerOutboxPayload;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorTriggerRuntimeImplTest {

    @Mock
    private ConnectorCatalog catalog;

    @Mock
    private CreateDossierUseCase createDossier;

    @Mock
    private GetDossierUseCase getDossier;

    @Mock
    private OutboxRepository outboxRepository;

    private ConnectorDefinition triggerDefinition;
    private TriggerConnector triggerConnector;
    private ConnectorTriggerRuntimeImpl runtime;

    @BeforeEach
    void setUp() {
        triggerDefinition =
                mock(ConnectorDefinition.class, withSettings().extraInterfaces(TriggerConnector.class));
        triggerConnector = (TriggerConnector) triggerDefinition;
        runtime = new ConnectorTriggerRuntimeImpl(catalog, createDossier, getDossier, outboxRepository);
    }

    @Test
    void createsDossierAndAppendsOutbox_withoutStartingWorkflow() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(triggerConnector.onEvent(eq(instance), any()))
                .thenReturn(List.of(new WorkflowTriggerEvent(
                        "smokeProcess", "SINISTRE", Map.of("a", 1), Map.of("v", 2), null)));
        UUID dossierId = UUID.randomUUID();
        when(createDossier.create(any(), any())).thenReturn(dossier(dossierId));

        runtime.onIncomingEvent(instance, Map.of("x", 1));

        ArgumentCaptor<CreateDossierUseCase.CreateDossierCommand> cmd =
                ArgumentCaptor.forClass(CreateDossierUseCase.CreateDossierCommand.class);
        verify(createDossier).create(cmd.capture(), any(AccessContext.class));
        assertThat(cmd.getValue().createdBy()).isEqualTo("trigger:" + instance.id());
        assertThat(cmd.getValue().dossierTypeCode()).isEqualTo("SINISTRE");

        ArgumentCaptor<TriggerOutboxPayload> payload = ArgumentCaptor.forClass(TriggerOutboxPayload.class);
        verify(outboxRepository).savePending(payload.capture());
        assertThat(payload.getValue().dossierId()).isEqualTo(dossierId);
        assertThat(payload.getValue().processKey()).isEqualTo("smokeProcess");
        assertThat(payload.getValue().connectorInstanceId()).isEqualTo(instance.id());
    }

    /** TODO §113 (D-MAILROOM-DEPOSIT) — un événement déjà construit est remis tel quel ; le dossier créé est renvoyé. */
    @Test
    void deliverHandsOverASingleEventAndReturnsTheDossier_onlyForATriggerConnector() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        UUID dossierId = UUID.randomUUID();
        when(createDossier.create(any(), any())).thenReturn(dossier(dossierId));

        assertThat(runtime.deliver(instance, new WorkflowTriggerEvent("triCourrier", "COURRIER", Map.of(), Map.of(), null)))
                .isEqualTo(new be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime.Delivery(dossierId, 0));
        verify(outboxRepository).savePending(any());

        when(catalog.get("http-rest")).thenReturn(mock(ConnectorDefinition.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.deliver(instance,
                        new WorkflowTriggerEvent("triCourrier", "COURRIER", Map.of(), Map.of(), null)))
                .isInstanceOf(be.myrmacta.domain.connector.model.ConnectorValidationException.class);
    }

    /** TODO §113 (D-MAILROOM-INDEX) — l'e-mail qui crée un courrier est inscrit dans l'index des fils (Message-ID et conversation). */
    @Test
    void anEmailEventRecordsItsIdentityForTheCreatedDossier() {
        MailMessageIndex index = mock(MailMessageIndex.class);
        var indexing = new ConnectorTriggerRuntimeImpl(catalog, createDossier, getDossier, outboxRepository, null, index,
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-31T08:00:00Z"), java.time.ZoneOffset.UTC));
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(triggerConnector.onEvent(eq(instance), any())).thenReturn(List.of(new WorkflowTriggerEvent("smokeProcess", "SINISTRE",
                Map.of(), Map.of(), null, List.of(), true,
                new WorkflowTriggerEvent.InboundMailIdentity("<m1@example.org>", "AAQkConv=", "Client@Example.org"))));
        UUID dossierId = UUID.randomUUID();
        when(createDossier.create(any(), any())).thenReturn(dossier(dossierId));

        indexing.onIncomingEvent(instance, Map.of());

        ArgumentCaptor<MailIndexEntry> entries = ArgumentCaptor.forClass(MailIndexEntry.class);
        verify(index, org.mockito.Mockito.times(2)).record(entries.capture());
        assertThat(entries.getAllValues()).extracting(MailIndexEntry::kind, MailIndexEntry::key, MailIndexEntry::direction)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MailIndexEntry.Kind.MESSAGE_ID, "<m1@example.org>", MailIndexEntry.Direction.IN),
                        org.assertj.core.groups.Tuple.tuple(MailIndexEntry.Kind.CONVERSATION, "AAQkConv=", MailIndexEntry.Direction.IN));
        assertThat(entries.getAllValues()).allSatisfy(entry -> {
            assertThat(entry.dossierId()).isEqualTo(dossierId);
            assertThat(entry.counterpart()).isEqualTo("client@example.org");
        });
    }

    @Test
    void existingReference_resolvesViaSystemAccess() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(triggerConnector.onEvent(eq(instance), any()))
                .thenReturn(List.of(new WorkflowTriggerEvent(
                        "smokeProcess", "SINISTRE", Map.of(), Map.of(), "DOS-1")));
        UUID dossierId = UUID.randomUUID();
        when(getDossier.getByReference(eq("tenant_dev"), eq("DOS-1"), any()))
                .thenReturn(dossier(dossierId));

        runtime.onIncomingEvent(instance, Map.of());

        verify(createDossier, never()).create(any(), any());
        verify(getDossier)
                .getByReference(
                        eq("tenant_dev"), eq("DOS-1"), eq(AccessContext.system("connector-trigger")));
        verify(outboxRepository).savePending(any());
    }

    @Test
    void unknownExistingReference_propagatesAndSkipsOutbox() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(triggerConnector.onEvent(eq(instance), any()))
                .thenReturn(List.of(new WorkflowTriggerEvent(
                        "smokeProcess", "SINISTRE", Map.of(), Map.of(), "MISSING")));
        when(getDossier.getByReference(any(), any(), any()))
                .thenThrow(new DossierNotFoundException("missing"));

        assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(DossierNotFoundException.class);
        verify(outboxRepository, never()).savePending(any());
    }

    @Test
    void nonTriggerDefinition_rejected() {
        var instance = instance();
        ConnectorDefinition serviceOnly = mock(ConnectorDefinition.class);
        when(catalog.get("http-rest")).thenReturn(serviceOnly);

        assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("TRIGGER");
    }

    @Test
    void unknownType_propagates() {
        var instance = instance();
        when(catalog.get("http-rest")).thenThrow(new UnknownConnectorException("http-rest"));
        assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(UnknownConnectorException.class);
    }

    private static ConnectorInstanceData instance() {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "n", "http-rest", true, Map.of());
    }

    private static Dossier dossier(UUID id) {
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-1",
                "SINISTRE",
                "label",
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0L,
                "system",
                java.time.Instant.parse("2024-01-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                null);
    }

    // ---- D3c : pièces jointes et référence inconnue ----

    @Test
    void attachmentsAreAttachedToTheDossier_andRejectionsAreVisibleToTheProcess() {
        var attach = mock(be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase.class);
        var withAttach = new ConnectorTriggerRuntimeImpl(catalog, createDossier, getDossier, outboxRepository, attach);
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        var file = new be.myrmacta.domain.connector.model.InboundAttachment("a.pdf", "application/pdf", new byte[] {1});
        when(triggerConnector.onEvent(eq(instance), any())).thenReturn(List.of(new WorkflowTriggerEvent(
                "p", "SINISTRE", Map.of(), Map.of("v", 1), null, List.of(file))));
        UUID dossierId = UUID.randomUUID();
        when(createDossier.create(any(), any())).thenReturn(dossier(dossierId));
        when(attach.attach(eq(dossierId), any(), any()))
                .thenReturn(new be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase.Report(0, List.of("a.pdf: virus")));

        withAttach.onIncomingEvent(instance, Map.of());

        ArgumentCaptor<TriggerOutboxPayload> payload = ArgumentCaptor.forClass(TriggerOutboxPayload.class);
        verify(outboxRepository).savePending(payload.capture());
        assertThat(payload.getValue().variables()).containsEntry("inboundAttachmentsRejected", 1).containsEntry("v", 1);
    }

    @Test
    void aRuntimeWithoutAttachmentSupportRefusesAnEventThatCarriesAttachments() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        var file = new be.myrmacta.domain.connector.model.InboundAttachment("a.pdf", "application/pdf", new byte[] {1});
        when(triggerConnector.onEvent(eq(instance), any())).thenReturn(List.of(new WorkflowTriggerEvent(
                "p", "SINISTRE", Map.of(), Map.of(), null, List.of(file))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(be.myrmacta.domain.connector.model.ConnectorValidationException.class);
        verify(createDossier, org.mockito.Mockito.never()).create(any(), any());
    }

    @Test
    void anUnknownReferenceCreatesADossierOnlyWhenTheEventAsksForIt() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(getDossier.getByReference(any(), eq("DOS-TYPO"), any()))
                .thenThrow(new be.myrmacta.domain.dossier.model.DossierNotFoundException("Dossier not found"));
        UUID created = UUID.randomUUID();
        when(createDossier.create(any(), any())).thenReturn(dossier(created));

        // mail : repli sur la création
        when(triggerConnector.onEvent(eq(instance), any())).thenReturn(List.of(new WorkflowTriggerEvent(
                "p", "SINISTRE", Map.of(), Map.of(), "DOS-TYPO", List.of(), true)));
        runtime.onIncomingEvent(instance, Map.of());
        verify(createDossier).create(any(), any());

        // autres connecteurs : comportement inchangé (échec)
        when(triggerConnector.onEvent(eq(instance), any())).thenReturn(List.of(new WorkflowTriggerEvent(
                "p", "SINISTRE", Map.of(), Map.of(), "DOS-TYPO")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(be.myrmacta.domain.dossier.model.DossierNotFoundException.class);
    }

    // ---- revue D3c/D3d, H2 : un connecteur d'interrogation acquitte APRÈS l'enregistrement ----

    private static final class RecordingPoller implements be.myrmacta.domain.connector.model.PollingTriggerConnector {
        final List<String> log = new java.util.ArrayList<>();
        final List<WorkflowTriggerEvent> toDeliver;

        RecordingPoller(List<WorkflowTriggerEvent> toDeliver) {
            this.toDeliver = toDeliver;
        }

        @Override
        public void poll(be.myrmacta.domain.connector.model.ConnectorInstanceData instance, EventDelivery delivery) {
            for (WorkflowTriggerEvent event : toDeliver) {
                try {
                    delivery.deliver(event);
                    log.add("acknowledged:" + event.processKey());
                } catch (RuntimeException failure) {
                    log.add("given back:" + event.processKey());
                }
            }
        }
    }

    @Test
    void aPollingConnectorIsAcknowledgedOnlyAfterTheEventIsPersisted_aFailureIsNotAcknowledged() {
        var poller = new RecordingPoller(List.of(
                new WorkflowTriggerEvent("ok1", "SINISTRE", Map.of(), Map.of(), null),
                new WorkflowTriggerEvent("bad", "INCONNU", Map.of(), Map.of(), null),
                new WorkflowTriggerEvent("ok3", "SINISTRE", Map.of(), Map.of(), null)));
        var pollerDefinition = new PollingDefinition(poller);
        when(catalog.get("http-rest")).thenReturn(pollerDefinition);
        UUID dossierId = UUID.randomUUID();
        when(createDossier.create(any(), any())).thenAnswer(inv -> {
            var cmd = (CreateDossierUseCase.CreateDossierCommand) inv.getArgument(0);
            if ("INCONNU".equals(cmd.dossierTypeCode())) {
                throw new IllegalArgumentException("unknown dossier type");
            }
            return dossier(dossierId);
        });

        runtime.onIncomingEvent(instance(), Map.of());

        // l'événement 2 échoue : il n'est pas acquitté, les événements 1 et 3 le sont, et le 3 est tout de même traité
        assertThat(poller.log).containsExactly("acknowledged:ok1", "given back:bad", "acknowledged:ok3");
        verify(outboxRepository, org.mockito.Mockito.times(2)).savePending(any());
    }

    /** Une définition de connecteur qui délègue au poller d'essai. */
    private static final class PollingDefinition implements ConnectorDefinition, be.myrmacta.domain.connector.model.PollingTriggerConnector {
        private final RecordingPoller poller;

        PollingDefinition(RecordingPoller poller) {
            this.poller = poller;
        }

        @Override public String getType() { return "http-rest"; }
        @Override public String getLabel() { return "poller"; }
        @Override public be.myrmacta.domain.connector.model.ConnectorCapability getCapability() {
            return be.myrmacta.domain.connector.model.ConnectorCapability.TRIGGER;
        }
        @Override public List<be.myrmacta.domain.connector.model.ConfigField> getConfigSchema() { return List.of(); }
        @Override public void validate(Map<String, Object> config) { }
        @Override public void poll(be.myrmacta.domain.connector.model.ConnectorInstanceData instance, EventDelivery delivery) {
            poller.poll(instance, delivery);
        }
    }

    @Test
    void anAttachmentStorageFailureNeverLeavesTheDossierWithoutItsProcess() {
        var attach = mock(be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase.class);
        var withAttach = new ConnectorTriggerRuntimeImpl(catalog, createDossier, getDossier, outboxRepository, attach);
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        var file = new be.myrmacta.domain.connector.model.InboundAttachment("a.pdf", "application/pdf", new byte[] {1});
        when(triggerConnector.onEvent(eq(instance), any())).thenReturn(List.of(new WorkflowTriggerEvent(
                "p", "SINISTRE", Map.of(), Map.of(), null, List.of(file))));
        when(createDossier.create(any(), any())).thenReturn(dossier(UUID.randomUUID()));
        when(attach.attach(any(), any(), any())).thenThrow(new IllegalStateException("storage down"));

        withAttach.onIncomingEvent(instance, Map.of());

        ArgumentCaptor<TriggerOutboxPayload> payload = ArgumentCaptor.forClass(TriggerOutboxPayload.class);
        verify(outboxRepository).savePending(payload.capture());
        assertThat(payload.getValue().variables()).containsEntry("inboundAttachmentsRejected", 1);
    }
}
