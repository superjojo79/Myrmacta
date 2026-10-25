package be.myrmacta.domain.workflow.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TriggerOutboxPayload(
        UUID dossierId,
        UUID connectorInstanceId,
        String processKey,
        Map<String, Object> variables,
        String triggerRef
) {
    public TriggerOutboxPayload {
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(processKey, "processKey");
        if (processKey.isBlank()) {
            throw new IllegalArgumentException("processKey must not be blank");
        }
        Objects.requireNonNull(triggerRef, "triggerRef");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
