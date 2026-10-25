package be.myrmacta.domain.connector.model;

import java.util.List;
import java.util.Objects;

public record ConfigField(
        String name,
        String label,
        ConfigFieldType type,
        boolean required,
        String defaultValue,
        List<String> options,
        String helpText
) {
    public ConfigField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
