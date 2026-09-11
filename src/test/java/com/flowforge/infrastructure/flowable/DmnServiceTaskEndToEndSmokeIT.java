package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.port.in.DeployDecisionTableUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §61 (introduction-dmn), T5.5 -- proves the full mechanism end to end on the real engine,
 * every layer exercised together rather than in isolation: {@link DeployDecisionTableUseCase}
 * (parsing + JUEL allowlist + deployment) deploys a real decision table, then the shipped fixture
 * {@code dmn-smoke.bpmn20.xml} ({@code serviceTask flowable:type="dmn"}, accepted by {@link
 * FlowableBpmnParserAdapter} D1) invokes it during a real process execution, and the decision's
 * output surfaces as a real process variable.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DmnServiceTaskEndToEndSmokeIT {

    private static final String DMN_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                         namespace="http://flowforge/test">
              <decision id="dmnSmokeEligibilite" name="Eligibilite">
                <decisionTable id="dt" hitPolicy="FIRST">
                  <input>
                    <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                  </input>
                  <output id="o" name="eligible" typeRef="string" />
                  <rule>
                    <inputEntry id="in1"><text>"GOLD"</text></inputEntry>
                    <outputEntry id="out1"><text>'ELIGIBLE'</text></outputEntry>
                  </rule>
                  <rule>
                    <inputEntry id="in2"><text>-</text></inputEntry>
                    <outputEntry id="out2"><text>'NOT_ELIGIBLE'</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    @Autowired
    private DeployDecisionTableUseCase deployDecisionTableUseCase;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void serviceTaskTypeDmn_evaluatesRealDeployedDecisionTable_duringProcessExecution() {
        DecisionTableDeploymentResult deployment =
                deployDecisionTableUseCase.deploy("dmn-smoke.dmn", DMN_XML.getBytes(StandardCharsets.UTF_8));
        assertThat(deployment.decisionKey()).isEqualTo("dmnSmokeEligibilite");

        String tenantId = tenantProperties.id();
        ProcessInstance eligible = runtimeService.startProcessInstanceByKeyAndTenantId(
                "dmnSmoke",
                "dmn-smoke-eligible-" + UUID.randomUUID(),
                Map.of("customerCat", "GOLD"),
                tenantId);
        ProcessInstance notEligible = runtimeService.startProcessInstanceByKeyAndTenantId(
                "dmnSmoke",
                "dmn-smoke-not-eligible-" + UUID.randomUUID(),
                Map.of("customerCat", "BRONZE"),
                tenantId);

        // start -> serviceTask -> end has no wait state: both instances have already completed.
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(eligible.getId()).count())
                .isZero();

        assertThat(historyService
                        .createHistoricVariableInstanceQuery()
                        .processInstanceId(eligible.getId())
                        .variableName("eligible")
                        .singleResult()
                        .getValue())
                .isEqualTo("ELIGIBLE");
        assertThat(historyService
                        .createHistoricVariableInstanceQuery()
                        .processInstanceId(notEligible.getId())
                        .variableName("eligible")
                        .singleResult()
                        .getValue())
                .isEqualTo("NOT_ELIGIBLE");
    }
}
