package be.myrmacta.infrastructure.connector.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleJsonPathMapperTest {

    private final SimpleJsonPathMapper mapper = new SimpleJsonPathMapper();

    @Test
    void resolvesNestedDotPath() {
        Map<String, Object> root = Map.of("claim", Map.of("id", "42", "amount", 10));
        assertThat(mapper.read(root, "$.claim.id")).isEqualTo("42");
        assertThat(mapper.read(root, "$.claim.amount")).isEqualTo(10);
    }

    @Test
    void resolvesArrayIndex() {
        Map<String, Object> root = Map.of("items", List.of(Map.of("x", "a"), Map.of("x", "b")));
        assertThat(mapper.read(root, "$.items[0].x")).isEqualTo("a");
        assertThat(mapper.read(root, "$.items[1].x")).isEqualTo("b");
    }

    @Test
    void rootPath_returnsWholeDocument() {
        Map<String, Object> root = Map.of("a", 1);
        assertThat(mapper.read(root, "$")).isEqualTo(root);
    }

    @Test
    void missingPath_returnsNull() {
        assertThat(mapper.read(Map.of("a", 1), "$.missing.child")).isNull();
    }

    @Test
    void invalidPath_rejected() {
        assertThatThrownBy(() -> mapper.read(Map.of(), "claim.id"))
                .isInstanceOf(ConnectorValidationException.class);
        assertThatThrownBy(() -> mapper.read(Map.of(), "$.a[?"))
                .isInstanceOf(ConnectorValidationException.class);
    }
}
