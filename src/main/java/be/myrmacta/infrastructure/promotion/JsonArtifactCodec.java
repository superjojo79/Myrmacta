package be.myrmacta.infrastructure.promotion;

import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §83 (D4) — JSON canonique des artefacts : propriétés et clés de table triées, indentation fixe, fin de ligne unique :
 * deux représentations égales donnent les mêmes octets (fonde l'« inchangé » et rend le bundle lisible dans un diff Git).
 * Lecture stricte : un champ inconnu est refusé (bundle d'une autre version ou forgé).
 */
public final class JsonArtifactCodec implements ArtifactCodec {

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Override
    public byte[] encode(Object doc) {
        return (mapper.writeValueAsString(doc) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T decode(byte[] content, Class<T> type) {
        try {
            return mapper.readValue(new String(content, StandardCharsets.UTF_8), type);
        } catch (JacksonException e) {
            throw new InvalidBundleException("unreadable JSON: " + e.getOriginalMessage(), e);
        } catch (IllegalArgumentException e) {
            if (e instanceof InvalidBundleException invalid) {
                throw invalid;
            }
            throw new InvalidBundleException("invalid content: " + e.getMessage(), e);
        }
    }
}
