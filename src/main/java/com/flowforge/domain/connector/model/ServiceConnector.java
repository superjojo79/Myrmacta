package com.flowforge.domain.connector.model;

import java.util.Map;

public interface ServiceConnector {

    Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams);
}
