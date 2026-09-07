package com.flowforge.domain.connector.usecase;

import com.flowforge.domain.connector.model.CallbackConnector;
import com.flowforge.domain.connector.model.CallbackResolution;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.port.in.CallbackConnectorRuntime;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
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
