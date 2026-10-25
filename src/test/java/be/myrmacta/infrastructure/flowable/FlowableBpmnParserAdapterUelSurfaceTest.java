package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * TODO §69 — allowlist générique des expressions UEL dans un document BPMN. Avant ce correctif,
 * seuls {@code assignee}/{@code candidateUsers}/{@code candidateGroups} (H1-bis),
 * {@code conditionExpression}, les listeners et {@code delegateExpression} étaient contrôlés,
 * alors que Flowable 8 évalue en UEL bien d'autres surfaces (preuve moteur :
 * {@link FlowableBpmnUelSurfaceGapTest}). Règle désormais : aucun {@code ${…}} / {@code #{…}}
 * nulle part dans le XML, sauf {@code delegateExpression} allowlistée et
 * {@code conditionExpression} à grammaire sûre — déjà validées par ailleurs.
 */
class FlowableBpmnParserAdapterUelSurfaceTest {

    private static final String BEAN = "${connectorServiceTask.getClass().getName()}";

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    @ParameterizedTest(name = "userTask {0}")
    @ValueSource(strings = {
            "name=\"" + BEAN + "\"",
            "flowable:formKey=\"" + BEAN + "\"",
            "flowable:dueDate=\"" + BEAN + "\"",
            "flowable:priority=\"" + BEAN + "\"",
            "flowable:category=\"" + BEAN + "\"",
            "flowable:owner=\"" + BEAN + "\"",
            "flowable:skipExpression=\"" + BEAN + "\"",
            "flowable:businessCalendarName=\"" + BEAN + "\"",
            "flowable:taskIdVariableName=\"" + BEAN + "\"",
            // Syntaxe différée : même évaluation par le moteur.
            "name=\"#{connectorServiceTask.getClass().getName()}\"",
            // Entité XML : le moteur voit le texte décodé, la règle doit le voir aussi.
            "name=\"&#36;{connectorServiceTask.getClass().getName()}\""
    })
    void userTaskAttributeExpression_isRejected(String attribute) {
        assertRejected(process("<userTask id=\"wait\" " + attribute + "/>"));
    }

    @Test
    void userTaskDocumentationExpression_isRejected() {
        assertRejected(process("<userTask id=\"wait\"><documentation>" + BEAN + "</documentation></userTask>"));
    }

    @Test
    void activitiNamespaceAlias_isRejected() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:activiti="http://activiti.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="wait" activiti:formKey="%s"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="wait"/>
                    <sequenceFlow sourceRef="wait" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(BEAN);
        assertRejected(xml);
    }

    @Test
    void startEventFormKeyExpression_isRejected() {
        assertRejected(wrap("<startEvent id=\"start\" flowable:formKey=\"" + BEAN + "\"/>"
                + "<endEvent id=\"end\"/><sequenceFlow sourceRef=\"start\" targetRef=\"end\"/>"));
    }

    @Test
    void timerExpression_isRejected() {
        assertRejected(process("<intermediateCatchEvent id=\"wait\"><timerEventDefinition><timeDuration>"
                + BEAN + "</timeDuration></timerEventDefinition></intermediateCatchEvent>"));
    }

    @Test
    void callActivityCalledElementExpression_isRejected() {
        assertRejected(process("<callActivity id=\"wait\" calledElement=\"" + BEAN + "\"/>"));
    }

    @Test
    void multiInstanceLoopCardinalityExpression_isRejected() {
        assertRejected(process("<userTask id=\"wait\"><multiInstanceLoopCharacteristics isSequential=\"true\">"
                + "<loopCardinality>" + BEAN + "</loopCardinality>"
                + "</multiInstanceLoopCharacteristics></userTask>"));
    }

    @Test
    void multiInstanceCollectionExpression_isRejected() {
        assertRejected(process("<userTask id=\"wait\"><multiInstanceLoopCharacteristics isSequential=\"true\" "
                + "flowable:collection=\"" + BEAN + "\" flowable:elementVariable=\"item\"/></userTask>"));
    }

    @Test
    void sequenceFlowSkipExpression_isRejected() {
        String body = "<startEvent id=\"start\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f\" sourceRef=\"start\" targetRef=\"end\" flowable:skipExpression=\""
                + BEAN + "\"/>";
        assertRejected(wrap(body));
    }

    @Test
    void classAttribute_isRejectedEvenWithoutExpression() {
        assertThatThrownBy(() -> parse(process("<sendTask id=\"wait\" flowable:class=\"java.lang.Object\"/>")))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("class");
    }

    @Test
    void literalValues_stillAccepted() {
        String userTask = "<userTask id=\"wait\" name=\"Valider la demande\" flowable:formKey=\"user-question:avis\""
                + " flowable:priority=\"50\" flowable:category=\"instruction\">"
                + "<documentation>Merci de vérifier les pièces.</documentation></userTask>";
        assertThat(parse(process(userTask))).isEqualTo("testProcess");
        assertThatCode(() -> parse(process("<intermediateCatchEvent id=\"wait\"><timerEventDefinition>"
                        + "<timeDuration>PT1H</timeDuration></timerEventDefinition></intermediateCatchEvent>")))
                .doesNotThrowAnyException();
    }

    @Test
    void allowlistedDelegateAndSafeCondition_stillAccepted() {
        String body = """
                <startEvent id="start"/>
                <serviceTask id="svc" flowable:delegateExpression="${connectorServiceTask}"/>
                <exclusiveGateway id="gw"/>
                <endEvent id="ok"/>
                <endEvent id="ko"/>
                <sequenceFlow sourceRef="start" targetRef="svc"/>
                <sequenceFlow sourceRef="svc" targetRef="gw"/>
                <sequenceFlow sourceRef="gw" targetRef="ok">
                  <conditionExpression xsi:type="tFormalExpression"><![CDATA[${decision == 'approve'}]]></conditionExpression>
                </sequenceFlow>
                <sequenceFlow sourceRef="gw" targetRef="ko">
                  <conditionExpression xsi:type="tFormalExpression">${decision != 'approve'}</conditionExpression>
                </sequenceFlow>
                """;
        assertThat(parse(wrap(body))).isEqualTo("testProcess");
    }

    /**
     * Flowable évalue une {@code conditionExpression} portant {@code language} comme un script
     * JSR-223 ({@code ConditionUtil} → {@code ScriptCondition}), sans {@code ${…}} : invisible pour
     * la grammaire de condition comme pour la règle « aucune expression ». Aucun moteur de script
     * n'est sur le classpath aujourd'hui (vérifié le 2026-09-24) — défense en profondeur contre
     * l'ajout futur, même transitif, de Groovy ou d'un moteur JavaScript.
     */
    @Test
    void scriptConditionLanguage_isRejected() {
        String body = """
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="f" sourceRef="start" targetRef="end">
                  <conditionExpression xsi:type="tFormalExpression" language="groovy">Runtime.runtime != null</conditionExpression>
                </sequenceFlow>
                """;
        assertThatThrownBy(() -> parse(wrap(body)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("language");
    }

    @Test
    void externalEntity_isNeverResolved() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE definitions [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start" name="&xxe;"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> parse(xml)).isInstanceOf(InvalidBpmnException.class);
    }

    private String parse(String xml) {
        return adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(String xml) {
        assertThatThrownBy(() -> parse(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("expression is not allowed");
    }

    /** Élément placé entre un startEvent et un endEvent. */
    private static String process(String element) {
        return wrap("<startEvent id=\"start\"/>" + element + "<endEvent id=\"end\"/>"
                + "<sequenceFlow sourceRef=\"start\" targetRef=\"wait\"/>"
                + "<sequenceFlow sourceRef=\"wait\" targetRef=\"end\"/>");
    }

    private static String wrap(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    %s
                  </process>
                </definitions>
                """.formatted(body);
    }
}
