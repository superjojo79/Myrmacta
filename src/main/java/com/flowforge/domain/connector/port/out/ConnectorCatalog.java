package com.flowforge.domain.connector.port.out;

import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import java.util.List;

public interface ConnectorCatalog {

    ConnectorDefinition get(String type);

    List<ConnectorDefinition> listAll();

    /**
     * Whether {@code key} is declared {@link ConfigFieldType#PASSWORD} in {@code type}'s config
     * schema — the single source of truth for which connector config fields are secret, used to
     * mask reads and encrypt at rest, instead of guessing from the field's name (adversarial
     * review, extension-mcp-flux-metier follow-up: a field like {@code authHeaderValue} carries a
     * real credential but doesn't match any name-based heuristic). Returns {@code false} rather
     * than throwing when {@code type} is unknown, so masking/encryption call sites never fail on
     * stale or unrecognized data.
     */
    default boolean isPasswordField(String type, String key) {
        ConnectorDefinition definition;
        try {
            definition = get(type);
        } catch (RuntimeException unknownType) {
            return false;
        }
        return definition != null
                && definition.getConfigSchema().stream()
                        .anyMatch(field -> isSecretType(field.type()) && field.name().equals(key));
    }

    private static boolean isSecretType(ConfigFieldType type) {
        return type == ConfigFieldType.PASSWORD || type == ConfigFieldType.SECRET_TEXTAREA;
    }
}
