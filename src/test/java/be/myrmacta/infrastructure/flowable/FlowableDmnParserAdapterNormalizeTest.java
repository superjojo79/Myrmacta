package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import be.myrmacta.domain.workflow.DmnXmlVersions;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FlowableDmnParserAdapterNormalizeTest {

    private final FlowableDmnParserAdapter adapter = new FlowableDmnParserAdapter();

    @Test
    void shippedGrille_afterTo13_stillPassesAllowlist() throws Exception {
        byte[] raw = Files.readAllBytes(Path.of("src/main/resources/decisions/grille-eligibilite-client.dmn"));
        byte[] converted = DmnXmlVersions.to13(raw);

        assertThat(new String(converted)).contains(DmnXmlVersions.DMN_13_NAMESPACE).doesNotContain("<!--");
        assertThat(adapter.extractDecisionKey(converted)).isEqualTo("grilleEligibiliteClient");
    }

    @Test
    void routage_afterTo13_passesAllowlist() throws Exception {
        byte[] converted =
                DmnXmlVersions.to13(Files.readAllBytes(Path.of("exemples/digital-mail-room/routage-courrier.dmn")));
        assertThatCode(() -> adapter.extractDecisionKey(converted)).doesNotThrowAnyException();
        assertThat(adapter.extractDecisionKey(converted)).isEqualTo("routageCourrier");
    }
}
