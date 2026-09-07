package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.flowforge.support.OidcClientTestConfiguration;

/**
 * TODO §57 revue adversariale PR #113 (H1 gap) : preuve d'exécution réelle, pas seulement une
 * lecture de code, que l'allowlist BPMN initiale de la PR #113 ne couvrait pas toutes les
 * surfaces UEL d'un document BPMN. Avant correctif, ce test déployait avec succès un {@code
 * userTask} dont {@code flowable:assignee="${connectorServiceTask}"} passait intégralement notre
 * validation (aucun {@code scriptTask}, aucun listener {@code class}/{@code expression}, aucun
 * {@code delegateExpression} hors allowlist), puis démarrait réellement sur le moteur Flowable et
 * observait {@code task.getAssignee()} égal à {@code connectorServiceTask.toString()} -- preuve
 * que l'expression UEL était évaluée, résolvant et invoquant un bean Spring vivant du contexte
 * applicatif via un champ que l'allowlist n'inspectait pas. Ce test fige le correctif : le même
 * document doit désormais être rejeté au niveau du parseur, avant même d'atteindre le moteur --
 * voir {@link FlowableBpmnParserAdapterTest} pour la couverture unitaire complète des nouveaux
 * vecteurs bloqués (assignee/candidateUsers/candidateGroups/conditionExpression/flowable:field).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class FlowableBpmnAllowlistGapTest {

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://flowforge/test">
              <process id="%s" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="wait" name="Wait" flowable:assignee="${connectorServiceTask}"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
                <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Autowired
    private BpmnParserPort bpmnParserPort;

    @Test
    void userTaskAssigneeExpression_referencingSpringBean_isRejectedBeforeReachingTheEngine() {
        String key = "allowlistGap" + System.nanoTime();
        byte[] bpmnXml = BPMN.formatted(key).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> bpmnParserPort.extractProcessDefinitionKey(bpmnXml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("assignee");
    }
}
