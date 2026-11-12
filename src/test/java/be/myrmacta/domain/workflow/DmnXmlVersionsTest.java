package be.myrmacta.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DmnXmlVersionsTest {

    private static final String DMN_11 =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                         namespace="http://myrmacta/test">
              <decision id="d1" name="D1">
                <decisionTable id="t1" hitPolicy="FIRST">
                  <input id="in1"><inputExpression id="in1e" typeRef="string"><text>x</text></inputExpression></input>
                  <output id="out1" name="y" typeRef="string"/>
                  <rule id="r1">
                    <inputEntry id="r1i"><text>"a"</text></inputEntry>
                    <outputEntry id="r1o"><text>"b"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    @Test
    void to13_replacesDmn11XmlnsWithDmn13() {
        String converted = DmnXmlVersions.to13(DMN_11);

        assertThat(converted).contains(DmnXmlVersions.DMN_13_NAMESPACE).doesNotContain(DmnXmlVersions.DMN_11_NAMESPACE);
        assertThat(converted).contains("decision id=\"d1\"").contains("\"a\"");
    }

    @Test
    void to13_leavesAlready13WithoutLeadingCommentUnchanged() {
        String already13 = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="%s" id="def"/>
                """.formatted(DmnXmlVersions.DMN_13_NAMESPACE);

        assertThat(DmnXmlVersions.to13(already13)).isEqualTo(already13);
    }

    @Test
    void to13_doesNotRewriteNamespaceUriInsideACell() {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def" namespace="http://t">
                  <decision id="d1" name="D1">
                    <decisionTable id="t1" hitPolicy="FIRST">
                      <input id="in1"><inputExpression id="in1e" typeRef="string"><text>x</text></inputExpression></input>
                      <output id="out1" name="y" typeRef="string"/>
                      <rule id="r1">
                        <inputEntry id="r1i"><text>"http://www.omg.org/spec/DMN/20151101"</text></inputEntry>
                        <outputEntry id="r1o"><text>"ok"</text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """;

        String converted = DmnXmlVersions.to13(xml);

        assertThat(converted)
                .contains("xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\"")
                .contains("\"http://www.omg.org/spec/DMN/20151101\"");
    }

    @Test
    void to13_doesNotRewriteCamundaSchemaLocationSuffix() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101"
                             xmlns:camunda="http://camunda.org/schema/1.0/dmn"
                             camunda:schemaLocation="http://www.omg.org/spec/DMN/20151101/dmn.xsd"
                             id="def"/>
                """;

        String converted = DmnXmlVersions.to13(xml);

        assertThat(converted)
                .contains("xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\"")
                .contains("http://www.omg.org/spec/DMN/20151101/dmn.xsd");
    }

    @Test
    void to13_bytesRoundTripPreservesUtf8() {
        byte[] converted = DmnXmlVersions.to13(DMN_11.getBytes(StandardCharsets.UTF_8));

        assertThat(new String(converted, StandardCharsets.UTF_8)).contains(DmnXmlVersions.DMN_13_NAMESPACE);
    }

    @Test
    void to13_nullAndBlankPassThrough() {
        assertThat(DmnXmlVersions.to13((String) null)).isNull();
        assertThat(DmnXmlVersions.to13("")).isEmpty();
        assertThat(DmnXmlVersions.to13((byte[]) null)).isNull();
    }

    @Test
    void to13_nonDmnTextIsReturnedUnchanged() {
        assertThat(DmnXmlVersions.to13("not a decision table")).isEqualTo("not a decision table");
    }

    @Test
    void to13_rewritesSingleQuotedXmlns() {
        String xml = "<definitions xmlns='" + DmnXmlVersions.DMN_11_NAMESPACE + "' id=\"def\"/>";
        assertThat(DmnXmlVersions.to13(xml))
                .contains("xmlns='" + DmnXmlVersions.DMN_13_NAMESPACE + "'")
                .doesNotContain(DmnXmlVersions.DMN_11_NAMESPACE);
    }

    @Test
    void to13_stripsLeadingXmlCommentsOnShippedGrille() throws Exception {
        String raw = Files.readString(Path.of("src/main/resources/decisions/grille-eligibilite-client.dmn"));
        assertThat(raw).contains("<!--").contains(DmnXmlVersions.DMN_11_NAMESPACE);

        String converted = DmnXmlVersions.to13(raw);

        assertThat(converted)
                .contains(DmnXmlVersions.DMN_13_NAMESPACE)
                .doesNotContain(DmnXmlVersions.DMN_11_NAMESPACE)
                .doesNotContain("<!--");
    }

    @Test
    void to13_stripsLeadingCommentsOnAlready13SoFlowableAccepts() {
        String xml = """
                <!-- generated by agent -->
                <definitions xmlns="%s" id="def">
                  <decision id="d1" name="D1">
                    <decisionTable id="t1" hitPolicy="FIRST">
                      <input id="in1"><inputExpression id="in1e" typeRef="string"><text>x</text></inputExpression></input>
                      <output id="out1" name="y" typeRef="string"/>
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(DmnXmlVersions.DMN_13_NAMESPACE);

        assertThat(DmnXmlVersions.to13(xml).stripLeading()).doesNotContain("<!--").startsWith("<definitions");
    }

    @Test
    void stripCommentsBeforeRoot_unclosedCommentDoesNotHang() {
        String hostile = "<!--".repeat(50_000) + "<definitions xmlns=\"" + DmnXmlVersions.DMN_11_NAMESPACE + "\"/>";

        long start = System.nanoTime();
        String result = DmnXmlVersions.to13(hostile);
        long ms = (System.nanoTime() - start) / 1_000_000L;

        assertThat(ms).as("linear scan must stay under 500ms for 50k unclosed openers").isLessThan(500);
        assertThat(result).contains("definitions");
    }

    @Test
    void stripCommentsBeforeRoot_preservesXmlDeclarationAndRemovesSeveralLeadingComments() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- one -->
                <!-- two -->
                <definitions xmlns="%s" id="def"/>
                """.formatted(DmnXmlVersions.DMN_13_NAMESPACE);

        String stripped = DmnXmlVersions.stripCommentsBeforeRoot(xml);

        assertThat(stripped)
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .doesNotContain("<!--")
                .contains("<definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\"");
    }

    @Test
    void stripCommentsBeforeRoot_keepsUnclosedXmlDeclarationIntact() {
        String xml = "<?xml version=\"1.0\"\n<definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/>";

        assertThat(DmnXmlVersions.stripCommentsBeforeRoot(xml)).isEqualTo(xml);
        assertThat(DmnXmlVersions.to13(xml)).isEqualTo(xml);
    }

    @Test
    void stripCommentsBeforeRoot_commentOnlyDocumentThatMentionsNamespaceYieldsEmptyBody() {
        // has13 is true because the URI appears in the comment; after stripping, nothing remains.
        String xml = "<!-- " + DmnXmlVersions.DMN_13_NAMESPACE + " -->";

        assertThat(DmnXmlVersions.to13(xml)).isEqualTo("");
    }

    @Test
    void stripCommentsBeforeRoot_whitespaceOnlyBeforeNamespaceTextIsPreserved() {
        String xml = "   " + DmnXmlVersions.DMN_13_NAMESPACE;

        assertThat(DmnXmlVersions.to13(xml)).isEqualTo(xml);
    }

    @Test
    void stripCommentsBeforeRoot_twoClosedCommentsAreBothRemoved() {
        String xml = "<!--A--><!--B--><definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/>";

        assertThat(DmnXmlVersions.stripCommentsBeforeRoot(xml))
                .isEqualTo("<definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/>");
    }

    @Test
    void stripCommentsBeforeRoot_emptyCommentIsRemoved() {
        // `<!---->` closes at searchFrom (= i+4); boundary mutant `end <= searchFrom` would keep it.
        String xml = "<!----><definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/>";

        assertThat(DmnXmlVersions.stripCommentsBeforeRoot(xml))
                .isEqualTo("<definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/>");
    }

    @Test
    void stripCommentsBeforeRoot_whitespaceOnlyDocumentIsPreserved() {
        String xml = "  \n\t  ";

        assertThat(DmnXmlVersions.stripCommentsBeforeRoot(xml)).isEqualTo(xml);
    }

    @Test
    void stripCommentsBeforeRoot_secondXmlDeclarationIsPreserved() {
        // A second declaration makes `searchFrom = i - open.length` (MathMutator) rediscover the
        // first `?>` and loop forever / drop content — PIT times that out as killed.
        String xml = "<?xml version=\"1.0\"?><?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/>";

        assertThat(DmnXmlVersions.stripCommentsBeforeRoot(xml)).isEqualTo(xml);
    }

    @Test
    void stripCommentsBeforeRoot_minimalXmlDeclarationIsPreserved() {
        // `<?xml?>` closes at searchFrom (= i+5); boundary mutant `end <= searchFrom` would drop it.
        String xml = "<?xml?><definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/>";

        assertThat(DmnXmlVersions.stripCommentsBeforeRoot(xml)).isEqualTo(xml);
    }

    @Test
    void stripCommentsBeforeRoot_commentAfterRootIsLeftAlone() {
        String xml = "<definitions xmlns=\"" + DmnXmlVersions.DMN_13_NAMESPACE + "\" id=\"def\"/><!-- trailing -->";

        assertThat(DmnXmlVersions.stripCommentsBeforeRoot(xml)).isEqualTo(xml);
    }

    @Test
    void to13_emptyByteArrayYieldsEmptyArray() {
        assertThat(DmnXmlVersions.to13(new byte[0])).isEmpty();
    }
}
