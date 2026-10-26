package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.ModelOutput;
import be.myrmacta.domain.precheck.port.out.ModelOutputReaderPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §106 (lot B, HC3) — lecture tolérante de la forme (bloc de code Markdown retiré, propriétés inconnues ignorées) mais
 * stricte sur les types : une valeur d'un autre type devient {@code null} et le domaine écarte le constat. Rien n'est
 * interprété au-delà de la structure attendue.
 */
final class JacksonModelOutputReader implements ModelOutputReaderPort {

    static final int MAX_JSON_CHARS = 200_000;

    private final ObjectMapper mapper;

    JacksonModelOutputReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ModelOutput> read(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON_CHARS) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = mapper.readTree(unfence(json.strip()));
        } catch (JacksonException invalid) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        List<ModelOutput.Item> items = new ArrayList<>();
        JsonNode findings = root.path("findings");
        if (findings.isArray()) {
            for (JsonNode node : findings) {
                if (node.isObject()) {
                    JsonNode confidence = node.path("confidence");
                    items.add(new ModelOutput.Item(text(node, "kind"), text(node, "sourceType"), text(node, "sourceRef"),
                            text(node, "message"), confidence.isNumber() ? confidence.asDouble() : null));
                }
            }
        }
        return Optional.of(new ModelOutput(text(root, "summary"), items));
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isString() ? value.asString() : null;
    }

    /** Certains modèles entourent le JSON d'un bloc {@code ```json … ```} malgré les consignes. */
    private static String unfence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int start = text.indexOf('\n');
        int end = text.lastIndexOf("```");
        return start < 0 || end <= start ? text : text.substring(start + 1, end).strip();
    }
}
