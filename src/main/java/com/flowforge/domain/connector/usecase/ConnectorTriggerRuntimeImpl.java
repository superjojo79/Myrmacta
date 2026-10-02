package com.flowforge.domain.connector.usecase;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.PollingTriggerConnector;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.domain.connector.port.in.AttachInboundFilesUseCase;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
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

    /** Sans rattachement de pièces jointes : un événement qui en porte est refusé (jamais ignoré en silence). */
    public ConnectorTriggerRuntimeImpl(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository) {
        this(catalog, createDossier, getDossier, outboxRepository, null);
    }

    public ConnectorTriggerRuntimeImpl(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository,
            AttachInboundFilesUseCase attachInboundFiles) {
        this.catalog = catalog;
        this.createDossier = createDossier;
        this.getDossier = getDossier;
        this.outboxRepository = outboxRepository;
        this.attachInboundFiles = attachInboundFiles;
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

    private UUID resolveOrCreateDossier(ConnectorInstanceData instance, WorkflowTriggerEvent event) {
        AccessContext.System system = AccessContext.system("connector-trigger");
        if (event.existingDossierReference() != null && !event.existingDossierReference().isBlank()) {
            try {
                return getDossier
                        .getByReference(instance.tenantId(), event.existingDossierReference(), system)
                        .id();
            } catch (com.flowforge.domain.dossier.model.DossierNotFoundException unknown) {
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
