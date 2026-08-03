package com.flowforge.infrastructure.connector.registry;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.UnknownConnectorException;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ConnectorRegistry implements ConnectorCatalog {

    private final List<ConnectorDefinition> definitions;
    private Map<String, ConnectorDefinition> catalogue = Map.of();

    public ConnectorRegistry(List<ConnectorDefinition> definitions) {
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }

    @PostConstruct
    public void init() {
        catalogue = definitions.stream()
                .collect(Collectors.toMap(
                        ConnectorDefinition::getType,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate connector type: " + a.getType());
                        },
                        LinkedHashMap::new));
    }

    @Override
    public ConnectorDefinition get(String type) {
        ConnectorDefinition definition = catalogue.get(type);
        if (definition == null) {
            throw new UnknownConnectorException(type);
        }
        return definition;
    }

    @Override
    public List<ConnectorDefinition> listAll() {
        return List.copyOf(catalogue.values());
    }
}
