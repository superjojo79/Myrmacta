package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidDmnException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Issue #204 (TODO §100, D-DMN-RESERVED-OUTPUT) — une table DMN ne peut pas écrire une variable réservée. Le moteur pose
 * chaque sortie d'un résultat unique sous le nom de sa colonne, et un résultat multiple sous l'id de la décision. Preuve
 * moteur : {@link ReservedDmnOutputIT}.
 */
class FlowableDmnParserAdapterReservedOutputTest {

    private final FlowableDmnParserAdapter adapter = new FlowableDmnParserAdapter();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "dossierId", "dossierWorkflowLinkId", "tenantId", "dossierFields", "connectorResult", "generatedDocumentId",
            // Espaces de bord : même nom pour la règle.
            " generatedDocumentId ",
    })
    void anOutputColumnNamedAfterAReservedVariable_isRejected(String output) {
        assertRejected(table("eligibilite", "UNIQUE", "<output id=\"o\" name=\"" + output + "\" typeRef=\"string\"/>",
                "<outputEntry id=\"out\"><text>'forged'</text></outputEntry>"), output.strip());
    }

    @Test
    void aReservedSecondOutputColumn_isRejected() {
        assertRejected(table("eligibilite", "FIRST",
                "<output id=\"o1\" name=\"result\" typeRef=\"string\"/>"
                        + "<output id=\"o2\" name=\"dossierId\" typeRef=\"string\"/>",
                "<outputEntry id=\"out1\"><text>'ok'</text></outputEntry>"
                        + "<outputEntry id=\"out2\"><text>'forged'</text></outputEntry>"), "dossierId");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"COLLECT", "UNIQUE"})
    void aDecisionIdNamedAfterAReservedVariable_isRejected(String hitPolicy) {
        // Plusieurs résultats : le moteur pose la liste sous l'id de la décision.
        assertRejected(table("connectorResult", hitPolicy, "<output id=\"o\" name=\"result\" typeRef=\"string\"/>",
                "<outputEntry id=\"out\"><text>'ok'</text></outputEntry>"), "connectorResult");
    }

    @Test
    void aDecisionServiceIdNamedAfterAReservedVariable_isRejected() {
        // Un service de décision se déploie comme une décision appelable : résultat multiple posé sous son id.
        String xml = table("eligibilite", "COLLECT", "<output id=\"o\" name=\"result\" typeRef=\"string\"/>",
                "<outputEntry id=\"out\"><text>'ok'</text></outputEntry>")
                .replace("</definitions>", "<decisionService id=\"dossierFields\" name=\"Service\">"
                        + "<outputDecision href=\"#eligibilite\"/></decisionService></definitions>")
                // L'élément n'existe qu'à partir de DMN 1.2.
                .replace("http://www.omg.org/spec/DMN/20151101", "https://www.omg.org/spec/DMN/20191111/MODEL/");
        assertRejected(xml, "dossierFields");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // Variable métier écrite aussi par une complétion : une table peut choisir le modèle de document.
            "documentType",
            "parentDossierId",
            // Flowable distingue la casse.
            "DossierId",
    })
    void anOrdinaryOutputColumn_isAccepted(String output) {
        String xml = table("eligibilite", "UNIQUE", "<output id=\"o\" name=\"" + output + "\" typeRef=\"string\"/>",
                "<outputEntry id=\"out\"><text>'ok'</text></outputEntry>");
        assertThat(adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8))).isEqualTo("eligibilite");
    }

    private void assertRejected(String xml, String reserved) {
        assertThatThrownBy(() -> adapter.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("DMN cannot write the reserved variable '" + reserved + "'");
    }

    private static String table(String decisionId, String hitPolicy, String outputs, String outputEntries) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="%s" name="Eligibilite">
                    <decisionTable id="dt" hitPolicy="%s">
                      <input><inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression></input>
                      %s
                      <rule>
                        <inputEntry id="in"><text>"GOLD"</text></inputEntry>
                        %s
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(decisionId, hitPolicy, outputs, outputEntries);
    }
}
