package be.myrmacta.domain.connector.model;

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
    /**
     * TODO §112 (D-ORCH-SERVICE-ONLY) : clé de configuration qui coupe le webhook entrant d'une instance (instance
     * « service seul »). Absente ou vraie : comportement d'avant.
     */
    public static final String WEBHOOK_ENABLED = "webhookEnabled";

    public ConnectorInstanceData {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(connectorType, "connectorType");
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    /** Faux seulement si la configuration coupe explicitement le webhook entrant ({@code false} ou {@code "false"}). */
    public boolean acceptsWebhooks() {
        Object value = config.get(WEBHOOK_ENABLED);
        return !(Boolean.FALSE.equals(value) || (value instanceof String text && "false".equalsIgnoreCase(text.strip())));
    }
}
