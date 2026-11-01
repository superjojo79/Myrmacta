package be.myrmacta.infrastructure.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** TODO §117 (D-PROMO-ORPHAN) — groupes nommés littéralement par un processus ou une table de routage. */
class XmlGroupReferenceExtractorTest {

    private final XmlGroupReferenceExtractor extractor = new XmlGroupReferenceExtractor();

    @Test
    void theMailRoomExampleNamesItsSortingGroupAndItsRoutingGroups() throws Exception {
        byte[] bpmn = Files.readAllBytes(Path.of("exemples/digital-mail-room/tri-courrier.bpmn20.xml"));
        byte[] dmn = Files.readAllBytes(Path.of("exemples/digital-mail-room/routage-courrier.dmn"));

        assertThat(extractor.fromBpmn(bpmn)).contains("COURRIER");
        assertThat(extractor.fromDmn(dmn)).contains("COMPTABILITE").doesNotContain("OUVRIR", "EMAIL");
    }

    @Test
    void literalCandidateGroupsAreRead_expressionsAndOtherShapesAreNot() {
        String bpmn = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">
                  <process id="p">
                    <userTask id="a" flowable:candidateGroups="RH, JURIDIQUE"/>
                    <userTask id="b" flowable:candidateGroups="${groupe}"/>
                    <userTask id="c" flowable:candidateGroups="minuscule"/>
                    <serviceTask id="d" flowable:groupeTri="TRI_CENTRAL"/>
                  </process>
                </definitions>
                """;
        assertThat(extractor.fromBpmn(bpmn.getBytes(StandardCharsets.UTF_8))).containsExactly("JURIDIQUE", "RH", "TRI_CENTRAL");
    }

    @Test
    void onlyTheRoutingOutputColumnIsRead() {
        String dmn = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/">
                  <decision id="d"><decisionTable>
                    <output name="routageAction"/><output name="routageService"/>
                    <rule><outputEntry><text>"OUVRIR"</text></outputEntry><outputEntry><text>"COMPTA"</text></outputEntry></rule>
                    <rule><outputEntry><text>'TRIER'</text></outputEntry><outputEntry><text>'RH'</text></outputEntry></rule>
                    <rule><outputEntry><text>"X"</text></outputEntry><outputEntry><text>variable</text></outputEntry></rule>
                  </decisionTable></decision>
                </definitions>
                """;
        assertThat(extractor.fromDmn(dmn.getBytes(StandardCharsets.UTF_8))).containsExactly("COMPTA", "RH");
    }

    @Test
    void anUnreadableOrDtdCarryingDocumentGivesNothing() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE d [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <definitions xmlns:flowable="http://flowable.org/bpmn"><userTask flowable:candidateGroups="&x;"/></definitions>
                """;
        assertThat(extractor.fromBpmn(xxe.getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(extractor.fromBpmn("<pas du xml".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(extractor.fromDmn(new byte[0])).isEmpty();
    }
}
