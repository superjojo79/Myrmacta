package be.myrmacta.domain.assistant.model;

import java.util.Map;
import java.util.Objects;

/** Un outil proposé au modèle : nom, description et schéma JSON des paramètres. */
public record ToolSpec(String name, String description, Map<String, Object> inputSchema) {

    public ToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputSchema, "inputSchema");
    }
}
