package com.flowforge.domain.connector.model;

import java.util.List;

public interface ConnectorDefinition {

    String getType();

    String getLabel();

    ConnectorCapability getCapability();

    List<ConfigField> getConfigSchema();

    void validate(java.util.Map<String, Object> config);
}
