package com.flowforge.domain.connector.usecase;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ConnectorTriggerRuntimeImpl implements ConnectorTriggerRuntime {

    private final ConnectorCatalog catalog;
    private final CreateDossierUseCase createDossier;
    private final GetDossierUseCase getDossier;
    private final OutboxRepository outboxRepository;

    public ConnectorTriggerRuntimeImpl(
            ConnectorCatalog catalog,
            CreateDossierUseCase createDossier,
            GetDossierUseCase getDossier,
            OutboxRepository outboxRepository) {
        this.catalog = catalog;
        this.createDossier = createDossier;
        this.getDossier = getDossier;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void onIncomingEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        ConnectorDefinition definition = catalog.get(instance.connectorType());
        if (!(definition instanceof TriggerConnector trigger)) {
            throw new ConnectorValidationException(
                    "Connector type does not support TRIGGER: " + instance.connectorType());
        }

        for (WorkflowTriggerEvent event : trigger.onEvent(instance, rawPayload)) {
            UUID dossierId = resolveOrCreateDossier(instance, event);
            outboxRepository.savePending(new TriggerOutboxPayload(
                    dossierId,
                    instance.id(),
                    event.processKey(),
                    event.variables(),
                    "connector:" + instance.id()));
        }
    }

    private UUID resolveOrCreateDossier(ConnectorInstanceData instance, WorkflowTriggerEvent event) {
        AccessContext.System system = AccessContext.system("connector-trigger");
        if (event.existingDossierReference() != null && !event.existingDossierReference().isBlank()) {
            return getDossier
                    .getByReference(instance.tenantId(), event.existingDossierReference(), system)
                    .id();
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
