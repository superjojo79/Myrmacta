package be.myrmacta.domain.connector.usecase;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorInstanceNotFoundException;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Synchronous SERVICE connector execution (D-SIGNAL / invoke, DD-EXECUTE-ROUTE).
 * Shared by the BFF and M2M URLs. POJO; no framework dependency.
 */
public final class ExecuteConnectorUseCaseImpl implements ExecuteConnectorUseCase {

    private final ConnectorInstanceRepository repository;
    private final ConnectorCatalog catalog;

    public ExecuteConnectorUseCaseImpl(
            ConnectorInstanceRepository repository, ConnectorCatalog catalog) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public Map<String, Object> execute(
            UUID instanceId, String tenantId, Map<String, Object> inputParams, ConnectorCallContext context) {
        Objects.requireNonNull(context, "context");
        ConnectorInstanceData instance = repository
                .findByIdAndTenantId(instanceId, tenantId)
                .filter(ConnectorInstanceData::enabled)
                .orElseThrow(() -> new ConnectorInstanceNotFoundException(instanceId));

        ConnectorDefinition definition = catalog.get(instance.connectorType());
        if (!definition.getCapability().includesService()) {
            throw new UnsupportedOperationException(
                    "Connector type does not support SERVICE: " + instance.connectorType());
        }
        if (!(definition instanceof ServiceConnector service)) {
            throw new UnsupportedOperationException(
                    "Connector type does not implement ServiceConnector: " + instance.connectorType());
        }
        Map<String, Object> params = inputParams == null ? Map.of() : inputParams;
        Map<String, Object> result = service.execute(instance, params, context);
        return result == null ? Map.of() : result;
    }
}
