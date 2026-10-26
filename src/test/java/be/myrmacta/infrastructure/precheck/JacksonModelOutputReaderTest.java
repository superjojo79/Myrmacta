package be.myrmacta.infrastructure.precheck;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.precheck.model.ModelOutput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §106 (lot B, HC3) — lecture tolérante de la forme, stricte sur les types. */
class JacksonModelOutputReaderTest {

    private final JacksonModelOutputReader reader = new JacksonModelOutputReader(JsonMapper.builder().build());

    @Test
    void readsTheExpectedStructure() {
        ModelOutput output = reader.read("""
                {"summary":"Deux points.","extra":1,"findings":[
                  {"kind":"MISSING","sourceType":"FIELD","sourceRef":"commune","message":"Absente.","confidence":0.5},
                  {"kind":"OK","sourceType":"QUESTION","sourceRef":"q","message":"Oui.","confidence":1}]}""").orElseThrow();
        assertThat(output.summary()).isEqualTo("Deux points.");
        assertThat(output.findings()).containsExactly(
                new ModelOutput.Item("MISSING", "FIELD", "commune", "Absente.", 0.5),
                new ModelOutput.Item("OK", "QUESTION", "q", "Oui.", 1.0));
    }

    @Test
    void stripsAMarkdownFence() {
        assertThat(reader.read("```json\n{\"summary\":\"s\",\"findings\":[]}\n```")).get()
                .extracting(ModelOutput::summary).isEqualTo("s");
    }

    @Test
    void wrongTypesBecomeNullForTheDomainToDiscard() {
        ModelOutput output = reader.read("""
                {"summary":42,"findings":[{"kind":["MISSING"],"sourceType":"FIELD","sourceRef":7,
                  "message":{"x":1},"confidence":"0.9"}, "pas un objet", null]}""").orElseThrow();
        assertThat(output.summary()).isNull();
        assertThat(output.findings()).containsExactly(new ModelOutput.Item(null, "FIELD", null, null, null));
    }

    @Test
    void anythingElseIsInvalid() {
        assertThat(reader.read(null)).isEmpty();
        assertThat(reader.read("  ")).isEmpty();
        assertThat(reader.read("Je ne peux pas répondre.")).isEmpty();
        assertThat(reader.read("[1,2]")).isEmpty();
        assertThat(reader.read("{\"summary\":\"s\"")).isEmpty();
        assertThat(reader.read("{\"summary\":\"" + "x".repeat(JacksonModelOutputReader.MAX_JSON_CHARS) + "\"}")).isEmpty();
        assertThat(reader.read("{\"summary\":\"s\",\"findings\":\"none\"}")).get()
                .extracting(ModelOutput::findings).asList().isEmpty();
    }
}
