package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * LC2 (TODO §75) — la brique « Évaluer une expression » sur le vrai moteur : le delegate évalue la formule (attribut
 * littéral), pose la variable de résultat, et une passerelle à condition sûre (identifiant nu) route dessus.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class BusinessExpressionSmokeIT {

    private static final String KEY = "businessExpressionSmoke";

    private static String process(String formula) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow sourceRef="start" targetRef="calc"/>
                    <serviceTask id="calc" flowable:delegateExpression="${businessExpressionTask}"
                                 flowable:formula="%s" flowable:formulaResult="highValue"/>
                    <sequenceFlow sourceRef="calc" targetRef="route"/>
                    <exclusiveGateway id="route" default="low"/>
                    <sequenceFlow id="high" sourceRef="route" targetRef="review">
                      <conditionExpression xsi:type="tFormalExpression"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">${highValue == true}</conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="low" sourceRef="route" targetRef="end"/>
                    <userTask id="review" name="Revue"/>
                    <sequenceFlow sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY, formula);
    }

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private BpmnParserPort parser;

    private void deploy(String formula) {
        byte[] xml = process(formula).getBytes(StandardCharsets.UTF_8);
        parser.extractProcessDefinitionKey(xml); // la même validation que le déploiement réel
        repositoryService.createDeployment().tenantId(tenantProperties.id())
                .addBytes(KEY + ".bpmn20.xml", xml).deploy();
    }

    private ProcessInstance start(Map<String, Object> variables) {
        return runtimeService.startProcessInstanceByKeyAndTenantId(
                KEY, "expr-" + UUID.randomUUID(), variables, tenantProperties.id());
    }

    @Test
    void theResultOfAFormulaRoutesAnExclusiveGatewayThroughASafeCondition() {
        deploy("variables.amount &gt; 1000");

        ProcessInstance high = start(Map.of("amount", 5000));
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(high.getId()).count()).isOne();
        assertThat(runtimeService.getVariable(high.getId(), "highValue")).isEqualTo(Boolean.TRUE);

        ProcessInstance low = start(Map.of("amount", 10));
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(low.getId()).count()).isZero();
        assertThat(historyService.createHistoricVariableInstanceQuery().processInstanceId(low.getId())
                        .variableName("highValue").singleResult().getValue())
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void aProcessVariableNamedLikeTheAttributeNeverReplacesTheDeployedFormula() {
        deploy("variables.amount &gt; 1000");

        // Un utilisateur qui complète une tâche contrôle des variables : `formula` ne doit jamais devenir du code.
        ProcessInstance p = start(Map.of("amount", 10, "formula", "true", "formulaResult", "amount"));

        assertThat(historyService.createHistoricVariableInstanceQuery().processInstanceId(p.getId())
                        .variableName("highValue").singleResult().getValue())
                .isEqualTo(Boolean.FALSE);
        assertThat(historyService.createHistoricVariableInstanceQuery().processInstanceId(p.getId())
                        .variableName("amount").singleResult().getValue())
                .isEqualTo(10);
    }

    @Test
    void aFormulaThatProducesNoValueFailsExplicitlyInsteadOfRoutingOnAnAbsentVariable() {
        deploy("variables.absent &gt; 1");

        assertThatThrownBy(() -> start(Map.of("amount", 10)))
                .hasMessageContaining("produced no value");
    }

    @Test
    void aBoundViolationAtRuntimeFailsTheTaskWithAClearMessage() {
        deploy("$pad('x', 2000000000) = 'y'");

        assertThatThrownBy(() -> start(Map.of("amount", 10))).hasMessageContaining("$pad");
    }
}
