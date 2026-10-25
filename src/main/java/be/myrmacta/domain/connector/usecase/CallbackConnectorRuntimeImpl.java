package be.myrmacta.domain.connector.usecase;

import be.myrmacta.domain.connector.model.CallbackConnector;
import be.myrmacta.domain.connector.model.CallbackHandlingConnector;
import be.myrmacta.domain.connector.model.CallbackResolution;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.port.in.CallbackConnectorRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import java.util.Objects;

public final class CallbackConnectorRuntimeImpl implements CallbackConnectorRuntime {

    private final ConnectorCatalog catalog;
    private final WorkflowEnginePort workflowEnginePort;

    public CallbackConnectorRuntimeImpl(ConnectorCatalog catalog, WorkflowEnginePort workflowEnginePort) {
        this.catalog = catalog;
        this.workflowEnginePort = workflowEnginePort;
    }

    @Override
    public void onIncomingCallback(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        ConnectorDefinition definition = catalog.get(instance.connectorType());
        if (definition instanceof CallbackHandlingConnector handling) {
            handling.handleCallback(instance, rawPayload);
            return;
        }
        if (!(definition instanceof CallbackConnector callback)) {
            throw new ConnectorValidationException(
                    "Connector type does not support CALLBACK: " + instance.connectorType());
        }

        CallbackResolution resolution = callback.resolve(instance, rawPayload);
        WorkflowVariables.assertNoReservedKeys(resolution.variables());
        workflowEnginePort.correlateMessage(
                resolution.messageName(),
                resolution.correlationVariableName(),
                resolution.correlationValue(),
                resolution.variables());
    }
}
