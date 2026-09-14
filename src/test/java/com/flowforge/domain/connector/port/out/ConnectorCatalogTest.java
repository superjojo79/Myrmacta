package com.flowforge.domain.connector.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.UnknownConnectorException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Direct coverage of {@link ConnectorCatalog#isPasswordField} — the default method itself was
 * previously exercised only indirectly (via {@code thenCallRealMethod()} in other tests). */
class ConnectorCatalogTest {

    private static final String TYPE = "fake-connector";

    private ConnectorCatalog catalogWith(ConfigField... schema) {
        ConnectorDefinition definition = new ConnectorDefinition() {
            @Override
            public String getType() {
                return TYPE;
            }

            @Override
            public String getLabel() {
                return "Fake";
            }

            @Override
            public com.flowforge.domain.connector.model.ConnectorCapability getCapability() {
                return com.flowforge.domain.connector.model.ConnectorCapability.TRIGGER;
            }

            @Override
            public List<ConfigField> getConfigSchema() {
                return List.of(schema);
            }

            @Override
            public void validate(Map<String, Object> config) {
                // no-op
            }
        };
        return new ConnectorCatalog() {
            @Override
            public ConnectorDefinition get(String type) {
                if (!TYPE.equals(type)) {
                    throw new UnknownConnectorException(type);
                }
                return definition;
            }

            @Override
            public List<ConnectorDefinition> listAll() {
                return List.of(definition);
            }
        };
    }

    @Test
    void passwordField_isDetected() {
        ConnectorCatalog catalog = catalogWith(
                new ConfigField("secret", "Secret", ConfigFieldType.PASSWORD, false, null, List.of(), null));

        assertThat(catalog.isPasswordField(TYPE, "secret")).isTrue();
    }

    @Test
    void secretTextareaField_isDetected() {
        ConnectorCatalog catalog = catalogWith(
                new ConfigField(
                        "privateKey", "Private key", ConfigFieldType.SECRET_TEXTAREA, false, null, List.of(), null));

        assertThat(catalog.isPasswordField(TYPE, "privateKey")).isTrue();
    }

    @Test
    void nonSecretField_isNotDetected() {
        ConnectorCatalog catalog = catalogWith(
                new ConfigField("host", "Host", ConfigFieldType.STRING, false, null, List.of(), null));

        assertThat(catalog.isPasswordField(TYPE, "host")).isFalse();
    }

    @Test
    void unknownConnectorType_returnsFalseRatherThanThrowing() {
        ConnectorCatalog catalog = catalogWith(
                new ConfigField("secret", "Secret", ConfigFieldType.PASSWORD, false, null, List.of(), null));

        assertThat(catalog.isPasswordField("does-not-exist", "secret")).isFalse();
    }

    @Test
    void unknownKey_returnsFalse() {
        ConnectorCatalog catalog = catalogWith(
                new ConfigField(
                        "privateKey", "Private key", ConfigFieldType.SECRET_TEXTAREA, false, null, List.of(), null));

        assertThat(catalog.isPasswordField(TYPE, "otherKey")).isFalse();
    }
}
