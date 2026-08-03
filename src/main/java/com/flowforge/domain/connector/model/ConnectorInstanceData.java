package com.flowforge.domain.connector.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ConnectorInstanceData(
        UUID id,
        String tenantId,
        String name,
        String connectorType,
        boolean enabled,
        Map<String, Object> config
) {
    public ConnectorInstanceData {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(connectorType, "connectorType");
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
