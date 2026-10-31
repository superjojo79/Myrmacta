package be.myrmacta.domain.connector.usecase;

import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.PollingTriggerConnector;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.domain.workflow.model.TriggerOutboxPayload;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ConnectorTriggerRuntimeImpl implements ConnectorTriggerRuntime {

    private final ConnectorCatalog catalog;
    private final CreateDossierUseCase createDossier;
    private final GetDossierUseCase getDossier;
    private final OutboxRepository outboxRepository;
    private final AttachInboundFilesUseCase attachInboundFiles;
    private final MailMessageIndex mailIndex;
    private final Clock clock;

    /** Sans rattachement de pièces jointes : un événement qui en porte est refusé (jamais ignoré en silence). */
    public ConnectorTriggerRuntimeImpl(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository) {
        this(catalog, createDossier, getDossier, outboxRepository, null, null, Clock.systemUTC());
    }

    public ConnectorTriggerRuntimeImpl(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository,
            AttachInboundFilesUseCase attachInboundFiles) {
        this(catalog, createDossier, getDossier, outboxRepository, attachInboundFiles, null, Clock.systemUTC());
    }

    /** TODO §113 (D-MAILROOM-INDEX) — avec l'index des fils : un e-mail qui crée un courrier y est inscrit. */
    public ConnectorTriggerRuntimeImpl(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository,
            AttachInboundFilesUseCase attachInboundFiles,
            MailMessageIndex mailIndex,
            Clock clock) {
        this.catalog = catalog;
        this.createDossier = createDossier;
        this.getDossier = getDossier;
        this.outboxRepository = outboxRepository;
        this.attachInboundFiles = attachInboundFiles;
        this.mailIndex = mailIndex;
        this.clock = clock;
    }

    @Override
    public void onIncomingEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        ConnectorDefinition definition = catalog.get(instance.connectorType());
        if (!(definition instanceof TriggerConnector trigger)) {
            throw new ConnectorValidationException(
                    "Connector type does not support TRIGGER: " + instance.connectorType());
        }

        if (trigger instanceof PollingTriggerConnector polling) {
            // Source interrogée : chaque événement est enregistré AVANT que le message ne soit rangé ou acquitté (revue D3c/D3d, H2).
            polling.poll(instance, event -> handle(instance, event));
            return;
        }
        for (WorkflowTriggerEvent event : trigger.onEvent(instance, rawPayload)) {
            handle(instance, event);
        }
    }

    private void handle(ConnectorInstanceData instance, WorkflowTriggerEvent event) {
        if (!event.attachments().isEmpty() && attachInboundFiles == null) {
            throw new ConnectorValidationException("attachments are not supported by this trigger runtime");
        }
        UUID dossierId = resolveOrCreateDossier(instance, event);
        recordInboundMail(instance, event, dossierId);
        Map<String, Object> variables = event.variables();
        if (!event.attachments().isEmpty()) {
            int rejected;
            try {
                rejected = attachInboundFiles.attach(dossierId, "connector:" + instance.id(), event.attachments()).rejections().size();
            } catch (RuntimeException storageFailure) {
                // Panne de stockage : le dossier existe déjà ; on ne le laisse pas sans processus, les pièces sont signalées perdues.
                rejected = event.attachments().size();
            }
            if (rejected > 0) {
                // Visible du processus : des pièces jointes ont été écartées (type, taille, contenu, antivirus, stockage).
                variables = new HashMap<>(variables);
                variables.put("inboundAttachmentsRejected", rejected);
            }
        }
        outboxRepository.savePending(new TriggerOutboxPayload(
                dossierId,
                instance.id(),
                event.processKey(),
                variables,
                "connector:" + instance.id()));
    }

    /** Un identifiant illisible ou trop long n'est pas inscrit : il ne rattachera rien, sans empêcher le courrier. */
    private void recordInboundMail(ConnectorInstanceData instance, WorkflowTriggerEvent event, UUID dossierId) {
        if (mailIndex == null || event.mail() == null) {
            return;
        }
        WorkflowTriggerEvent.InboundMailIdentity mail = event.mail();
        record(instance, MailIndexEntry.Kind.MESSAGE_ID, mail.messageId(), dossierId, mail.sender());
        record(instance, MailIndexEntry.Kind.CONVERSATION, mail.conversationId(), dossierId, mail.sender());
    }

    private void record(ConnectorInstanceData instance, MailIndexEntry.Kind kind, String key, UUID dossierId, String sender) {
        if (key == null || key.isBlank() || key.length() > MailIndexEntry.MAX_KEY_CHARS) {
            return;
        }
        mailIndex.record(new MailIndexEntry(instance.tenantId(), kind, key.strip(), MailIndexEntry.Direction.IN, dossierId, sender,
                clock.instant()));
    }

    private UUID resolveOrCreateDossier(ConnectorInstanceData instance, WorkflowTriggerEvent event) {
        AccessContext.System system = AccessContext.system("connector-trigger");
        if (event.existingDossierReference() != null && !event.existingDossierReference().isBlank()) {
            try {
                return getDossier
                        .getByReference(instance.tenantId(), event.existingDossierReference(), system)
                        .id();
            } catch (be.myrmacta.domain.dossier.model.DossierNotFoundException unknown) {
                if (!event.createWhenReferenceUnknown()) {
                    throw unknown;
                }
                // Référence inconnue (faute de frappe) : le message devient un nouveau dossier plutôt que d'être perdu.
            }
        }
        String typeCode = event.dossierTypeCode();
        if (typeCode == null || typeCode.isBlank()) {
            throw new ConnectorValidationException(
                    "dossierTypeCode is required when existingDossierReference is absent");
        }
        Dossier created = createDossier.create(
                new CreateDossierUseCase.CreateDossierCommand(
                        instance.tenantId(),
                        typeCode,
                        "trigger:" + instance.id(),
                        event.dossierFields() == null ? Map.of() : event.dossierFields(),
                        "trigger:" + instance.id()),
                system);
        return created.id();
    }
}
