package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** TODO §127 (D-DEMO-LOCALE, D-DEMO-LOCALE-FILES) — lecture d'un exemple de la démo dans la langue demandée. */
class DemoExampleTest {

    @TempDir
    Path dir;

    @BeforeEach
    void anExample() throws IOException {
        Files.createDirectories(dir.resolve("config"));
        Files.createDirectories(dir.resolve("en/config"));
        Files.writeString(dir.resolve("config/type.json"), "{\"label\":\"Courrier entrant\"}");
        Files.writeString(dir.resolve("config/codes.json"), "{\"code\":\"COURRIER\"}");
        Files.writeString(dir.resolve("en/config/type.json"), "{\"label\":\"Incoming mail\"}");
        Files.writeString(dir.resolve("process.bpmn20.xml"),
                "<userTask id=\"trier\" name=\"Trier le courrier\"/><description>Sans règle</description>"
                        + "<flowable:field name=\"groupeTri\"/>");
        Files.writeString(dir.resolve("en/names.json"),
                "{\"Trier le courrier\":\"Sort the mail\",\"Sans règle\":\"No rule\",\"groupeTri\":\"groupeTri\"}");
    }

    private DemoExample example(String locale) {
        return new DemoExample(dir, locale, JsonMapper.builder().build());
    }

    @Test
    void inFrench_theFilesAreReadAsTheyAre() {
        assertThat(example("fr").json("config/type.json").get("label").asString()).isEqualTo("Courrier entrant");
        assertThat(example("fr").xml("process.bpmn20.xml")).contains("name=\"Trier le courrier\"");
        assertThat(example("fr").english()).isFalse();
    }

    @Test
    void inEnglish_theTranslatedCopyIsRead_andAFileWithoutCopyIsReadAsItIs() {
        assertThat(example("en").json("config/type.json").get("label").asString()).isEqualTo("Incoming mail");
        assertThat(example("en").json("config/codes.json").get("code").asString()).isEqualTo("COURRIER");
    }

    @Test
    void inEnglish_namesAndDescriptionsOfAProcessAreTranslated() {
        String xml = example("en").xml("process.bpmn20.xml");

        assertThat(xml).contains("name=\"Sort the mail\"").contains("<description>No rule</description>")
                .contains("<flowable:field name=\"groupeTri\"/>");
        assertThat(new String(example("en").bytes("process.bpmn20.xml"), StandardCharsets.UTF_8)).isEqualTo(xml);
    }

    @Test
    void textChoosesTheLanguage() {
        assertThat(example("fr").text("Facture", "Invoice")).isEqualTo("Facture");
        assertThat(example("en").text("Facture", "Invoice")).isEqualTo("Invoice");
    }

    @Test
    void anUnknownLanguageIsRefused() {
        assertThatThrownBy(() -> example("de")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("de");
    }
}
