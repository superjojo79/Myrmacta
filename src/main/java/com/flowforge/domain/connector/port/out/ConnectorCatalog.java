package com.flowforge.domain.connector.port.out;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import java.util.List;

public interface ConnectorCatalog {

    ConnectorDefinition get(String type);

    List<ConnectorDefinition> listAll();
}
