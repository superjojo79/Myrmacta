package be.myrmacta.domain.connector.model;

import java.util.Map;
import java.util.Objects;

public record CallbackResolution(
        String messageName,
        String correlationVariableName,
        Object correlationValue,
        Map<String, Object> variables) {

    public CallbackResolution {
        Objects.requireNonNull(messageName, "messageName");
        Objects.requireNonNull(correlationVariableName, "correlationVariableName");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
