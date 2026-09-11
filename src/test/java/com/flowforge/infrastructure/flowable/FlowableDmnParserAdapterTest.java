package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.model.InvalidDmnException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FlowableDmnParserAdapterTest {

    private static final String MALFORMED_XML = "<not-even-xml";

    private static final String NON_DMN_XML = "<root><child/></root>";

    private final FlowableDmnParserAdapter adapter = new FlowableDmnParserAdapter();

    @Test
    void extractDecisionKey_validTable_returnsDecisionId() {
        String xml = decisionTable("UNIQUE", "== \"BRONZE\"", "'2000'");
        assertThat(adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8))).isEqualTo("eligibilite");
    }

    @Test
    void extractDecisionKey_bareValueImplicitEquality_isAccepted() {
        // No leading operator -- Flowable treats the whole cell as "== value".
        String xml = decisionTable("UNIQUE", "\"BRONZE\"", "'2000'");
        assertThat(adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8))).isEqualTo("eligibilite");
    }

    @Test
    void extractDecisionKey_numberAndComparisonOperators_areAccepted() {
        for (String cell : new String[] {">= 18", "<= 65", "!= 3", "< 100", "> -5", "42", "true", "false"}) {
            String xml = decisionTable("UNIQUE", cell, "'ok'");
            assertThat(adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                    .as("cell: %s", cell)
                    .isEqualTo("eligibilite");
        }
    }

    @Test
    void extractDecisionKey_emptyOrDashCell_isAccepted() {
        for (String cell : new String[] {"", "-"}) {
            String xml = decisionTable("UNIQUE", cell, "'ok'");
            assertThat(adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                    .as("cell: [%s]", cell)
                    .isEqualTo("eligibilite");
        }
    }

    @Test
    void extractDecisionKey_malformedXml_throwsInvalidDmnException() {
        assertThatThrownBy(() -> adapter.extractDecisionKey(MALFORMED_XML.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class);
    }

    @Test
    void extractDecisionKey_wellFormedButNotDmn_throwsInvalidDmnException() {
        assertThatThrownBy(() -> adapter.extractDecisionKey(NON_DMN_XML.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class);
    }

    @Test
    void extractDecisionKey_bareUnquotedIdentifier_isRejected_H1BisEquivalent() {
        // Same vulnerability class as TODO §57 H1-bis: an unquoted identifier resolves as a UEL
        // property/bean lookup, not a string literal, once wrapped by Flowable's own pre-parser.
        String xml = decisionTable("UNIQUE", "connectorServiceTask", "'ok'");
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("inputEntry");
    }

    @Test
    void extractDecisionKey_operatorFollowedByBareIdentifier_isRejected() {
        String xml = decisionTable("UNIQUE", "== connectorServiceTask", "'ok'");
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class);
    }

    @Test
    void extractDecisionKey_rawElPassthroughSyntax_isRejected() {
        for (String cell : new String[] {"#{connectorServiceTask}", "${connectorServiceTask}"}) {
            String xml = decisionTable("UNIQUE", cell, "'ok'");
            assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                    .as("cell: %s", cell)
                    .isInstanceOf(InvalidDmnException.class)
                    .hasMessageContaining("raw EL");
        }
    }

    @Test
    void extractDecisionKey_propertyNavigationPrefix_isRejected() {
        String xml = decisionTable("UNIQUE", ".class.someMethod", "'ok'");
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("property navigation");
    }

    @Test
    void extractDecisionKey_functionInvocationCell_isRejected() {
        // Flowable itself supports fn_now()/fn_addDate() date functions -- deliberately out of
        // scope: any parenthesis is rejected by the safe-value grammar, same posture as BPMN's
        // conditionExpression allowlist.
        String xml = decisionTable("UNIQUE", "> fn_addDate(fn_now(),1,2,3)", "'ok'");
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class);
    }

    @Test
    void extractDecisionKey_unsafeOutputEntry_isRejected() {
        String xml = decisionTable("UNIQUE", "\"BRONZE\"", "connectorServiceTask");
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("outputEntry");
    }

    @Test
    void extractDecisionKey_disallowedHitPolicy_isRejected() {
        String xml = decisionTable("PRIORITY", "\"BRONZE\"", "'ok'");
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("hit policy");
    }

    @Test
    void extractDecisionKey_collectHitPolicy_isAllowed() {
        String xml = decisionTable("COLLECT", "\"BRONZE\"", "'ok'");
        assertThat(adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8))).isEqualTo("eligibilite");
    }

    @Test
    void extractDecisionKey_firstHitPolicy_isAllowed() {
        String xml = decisionTable("FIRST", "\"BRONZE\"", "'ok'");
        assertThat(adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8))).isEqualTo("eligibilite");
    }

    @Test
    void extractDecisionKey_multipleDecisions_isRejected() {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="d1" name="D1">
                    <decisionTable id="dt1" hitPolicy="UNIQUE">
                      <input><inputExpression id="ie1" typeRef="string"><text>customerCat</text></inputExpression></input>
                      <output id="o1" name="result" typeRef="string" />
                      <rule>
                        <inputEntry id="in1"><text>"BRONZE"</text></inputEntry>
                        <outputEntry id="out1"><text>'ok'</text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                  <decision id="d2" name="D2">
                    <decisionTable id="dt2" hitPolicy="UNIQUE">
                      <input><inputExpression id="ie2" typeRef="string"><text>customerCat</text></inputExpression></input>
                      <output id="o2" name="result" typeRef="string" />
                      <rule>
                        <inputEntry id="in2"><text>"GOLD"</text></inputEntry>
                        <outputEntry id="out2"><text>'ok'</text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """;
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("exactly one decision");
    }

    @Test
    void extractDecisionKey_decisionWithNoIdAttribute_isRejected() {
        // Phase 8 gap : decision.getId() null/blank guard (extractDecisionKey) never exercised.
        // An empty id="" fails DMN XSD validation itself (NCName pattern) before reaching our own
        // check -- omitting the attribute entirely (optional per the DMN 1.1 schema) is the only
        // schema-valid way to reach decision.getId() == null.
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision name="Eligibilite">
                    <decisionTable id="dt" hitPolicy="UNIQUE">
                      <input><inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression></input>
                      <output id="o" name="result" typeRef="string" />
                      <rule>
                        <inputEntry id="in"><text>"BRONZE"</text></inputEntry>
                        <outputEntry id="out"><text>'ok'</text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """;
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("does not define an id");
    }

    @Test
    void extractDecisionKey_decisionWithLiteralExpressionInsteadOfTable_isRejected() {
        // Phase 8 gap : DMN allows a <decision> to carry a <literalExpression> instead of a
        // <decisionTable> -- never exercised. Our allowlist only reasons about decision tables.
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="eligibilite" name="Eligibilite">
                    <literalExpression id="le"><text>1</text></literalExpression>
                  </decision>
                </definitions>
                """;
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("must be a decision table");
    }

    @Test
    void extractDecisionKey_decisionTableWithNoRules_isRejected() {
        // Phase 8 gap : empty decisionTable (0 <rule> elements) never exercised.
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="eligibilite" name="Eligibilite">
                    <decisionTable id="dt" hitPolicy="UNIQUE">
                      <input><inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression></input>
                      <output id="o" name="result" typeRef="string" />
                    </decisionTable>
                  </decision>
                </definitions>
                """;
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("has no rules");
    }

    // Phase 8 gap investigated, not covered by design : assertSafeCellText's rawText==null
    // short-circuit (line 97-98) has no reachable test through the public, schema-validated
    // parsing path -- DMN 1.1's XSD requires a <text> child on every inputEntry/outputEntry
    // (verified: an <inputEntry id="in"/> with no <text> at all fails schema validation before
    // reaching our code, "content of element 'inputEntry' is not complete... text is expected").
    // The only way to produce rawText == null would be to call assertSafeCellText directly
    // (private, would need reflection) with a hand-built model bypassing XML entirely --
    // disproportionate to prove defensive code that the real parsing path cannot exercise.
    // Left uncovered deliberately, documented here rather than forced by a synthetic unit test.

    private static String decisionTable(String hitPolicy, String inputEntryText, String outputEntryText) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="eligibilite" name="Eligibilite">
                    <decisionTable id="dt" hitPolicy="%s">
                      <input>
                        <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                      </input>
                      <output id="o" name="result" typeRef="string" />
                      <rule>
                        <inputEntry id="in"><text><![CDATA[%s]]></text></inputEntry>
                        <outputEntry id="out"><text><![CDATA[%s]]></text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """
                .formatted(hitPolicy, inputEntryText, outputEntryText);
    }
}
