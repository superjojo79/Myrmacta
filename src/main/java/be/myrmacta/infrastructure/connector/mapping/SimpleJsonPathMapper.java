package be.myrmacta.infrastructure.connector.mapping;

import be.myrmacta.domain.connector.model.ConnectorValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Minimal JSONPath subset: {@code $}, {@code $.a.b}, {@code $.items[0].x}.
 * No filters / scripts (DD-JSONPATH).
 */
@Component
public class SimpleJsonPathMapper {

    private static final Pattern SEGMENT =
            Pattern.compile("([A-Za-z0-9_]+)(?:\\[(\\d+)])?");

    public Object read(Object root, String path) {
        if (path == null || path.isBlank()) {
            throw new ConnectorValidationException("JSONPath is required");
        }
        if (!path.startsWith("$")) {
            throw new ConnectorValidationException("JSONPath must start with $: " + path);
        }
        if (path.contains("?") || path.contains("(") || path.contains("..")) {
            throw new ConnectorValidationException("Unsupported JSONPath features: " + path);
        }
        if ("$".equals(path)) {
            return root;
        }
        if (!path.startsWith("$.")) {
            throw new ConnectorValidationException("Invalid JSONPath: " + path);
        }
        Object current = root;
        for (String rawSegment : path.substring(2).split("\\.")) {
            if (rawSegment.isBlank()) {
                throw new ConnectorValidationException("Invalid JSONPath segment in: " + path);
            }
            Matcher matcher = SEGMENT.matcher(rawSegment);
            if (!matcher.matches()) {
                throw new ConnectorValidationException("Invalid JSONPath segment: " + rawSegment);
            }
            String field = matcher.group(1);
            current = readField(current, field);
            if (current == null) {
                return null;
            }
            if (matcher.group(2) != null) {
                int index = Integer.parseInt(matcher.group(2));
                current = readIndex(current, index);
                if (current == null) {
                    return null;
                }
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Object readField(Object current, String field) {
        if (current instanceof Map<?, ?> map) {
            return map.get(field);
        }
        return null;
    }

    private static Object readIndex(Object current, int index) {
        if (current instanceof List<?> list) {
            if (index < 0 || index >= list.size()) {
                return null;
            }
            return list.get(index);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Object> readItems(Object root, String itemsPath) {
        Object value = read(root, itemsPath == null || itemsPath.isBlank() ? "$" : itemsPath);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }
}
