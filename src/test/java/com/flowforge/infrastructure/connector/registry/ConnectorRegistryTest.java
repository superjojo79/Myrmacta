package com.flowforge.infrastructure.connector.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.UnknownConnectorException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorRegistryTest {

    @Test
    void indexesDefinitionsByType() {
        ConnectorDefinition http = stub("http-rest", ConnectorCapability.TRIGGER);
        ConnectorDefinition signal = stub("flowable-signal", ConnectorCapability.BOTH);
        var registry = new ConnectorRegistry(List.of(http, signal));
        registry.init();

        assertThat(registry.get("http-rest")).isSameAs(http);
        assertThat(registry.listAll()).containsExactlyInAnyOrder(http, signal);
    }

    @Test
    void unknownType_rejected() {
        var registry = new ConnectorRegistry(List.of(stub("http-rest", ConnectorCapability.TRIGGER)));
        registry.init();
        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOf(UnknownConnectorException.class);
    }

    private static ConnectorDefinition stub(String type, ConnectorCapability capability) {
        return new ConnectorDefinition() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getLabel() {
                return type;
            }

            @Override
            public ConnectorCapability getCapability() {
                return capability;
            }

            @Override
            public List<ConfigField> getConfigSchema() {
                return List.of(new ConfigField("x", "X", ConfigFieldType.STRING, false, null, List.of(), null));
            }

            @Override
            public void validate(Map<String, Object> config) {}
        };
    }
}
