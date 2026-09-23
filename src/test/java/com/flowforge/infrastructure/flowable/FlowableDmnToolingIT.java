package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import com.flowforge.domain.workflow.model.InvalidDmnException;
import com.flowforge.domain.workflow.port.in.EvaluateDecisionUseCase;
import com.flowforge.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import com.flowforge.domain.workflow.port.in.RedeployDecisionTableUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.dmn.api.DmnRepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class FlowableDmnToolingIT {

    private static final AccessContext.User ADMIN = AccessContext.user("dmn-it", Set.of("TENANT_ADMIN"));

    private static final String DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                         namespace="http://flowforge/test">
              <decision id="%s" name="Def">
                <decisionTable id="dt" hitPolicy="UNIQUE">
                  <input>
                    <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                  </input>
                  <output id="o" name="result" typeRef="string" />
                  <rule>
                    <inputEntry id="in"><text>"BRONZE"</text></inputEntry>
                    <outputEntry id="out"><text>'%s'</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private FlowableDmnDeploymentAdapter deploymentAdapter;

    @Autowired
    private DmnRepositoryService dmnRepositoryService;

    @Autowired
    private EvaluateDecisionUseCase evaluateDecisionUseCase;

    @Autowired
    private ListDecisionTableVersionsUseCase listVersionsUseCase;

    @Autowired
    private RedeployDecisionTableUseCase redeployDecisionTableUseCase;

    @Test
    void evaluate_afterDeploy_returnsTheMatchingRow() {
        String key = "itEval" + System.nanoTime();
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "ok"));

        DecisionEvaluationResult result =
                evaluateDecisionUseCase.evaluate(ADMIN, key, null, Map.of("customerCat", "BRONZE"));

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.rows()).containsExactly(Map.of("result", "ok"));
    }

    @Test
    void evaluate_explicitOlderVersion_doesNotReturnTheLatestOutput() {
        String key = "itVer" + System.nanoTime();
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "old"));
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "new"));

        DecisionEvaluationResult older =
                evaluateDecisionUseCase.evaluate(ADMIN, key, 1, Map.of("customerCat", "BRONZE"));
        DecisionEvaluationResult latest =
                evaluateDecisionUseCase.evaluate(ADMIN, key, null, Map.of("customerCat", "BRONZE"));

        assertThat(older.rows()).containsExactly(Map.of("result", "old"));
        assertThat(latest.version()).isEqualTo(2);
        assertThat(latest.rows()).containsExactly(Map.of("result", "new"));
    }

    @Test
    void listVersions_reflectsDeploymentsInAscendingOrder() {
        String key = "itHist" + System.nanoTime();
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "a"));
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "b"));

        List<DecisionTableVersionInfo> versions = listVersionsUseCase.list(ADMIN, key);

        assertThat(versions).extracting(DecisionTableVersionInfo::version).containsExactly(1, 2);
        assertThat(versions).allSatisfy(info -> {
            assertThat(info.deploymentId()).isNotBlank();
            assertThat(info.deployedAt()).isNotNull();
        });
    }

    @Test
    void redeploy_versionOne_becomesANewCurrentVersionWithTheSameXml() {
        String key = "itRb" + System.nanoTime();
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "old"));
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "new"));

        var deployed = redeployDecisionTableUseCase.redeploy(ADMIN, key, 1);

        assertThat(deployed.deployedVersion()).isEqualTo(3);
        String latestXml = new String(
                deploymentAdapter.findDeployedXml(tenantProperties.id(), key).orElseThrow(),
                StandardCharsets.UTF_8);
        assertThat(latestXml).contains("'old'");
        DecisionEvaluationResult current =
                evaluateDecisionUseCase.evaluate(ADMIN, key, null, Map.of("customerCat", "BRONZE"));
        assertThat(current.rows()).containsExactly(Map.of("result", "old"));
    }

    @Test
    void redeploy_historicalXmlOutsideTheAllowlist_isRefused() {
        String key = "itAllow" + System.nanoTime();
        String unsafe = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="%s" name="Gap">
                    <decisionTable id="dt" hitPolicy="UNIQUE">
                      <input>
                        <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                      </input>
                      <output id="o" name="result" />
                      <rule>
                        <inputEntry id="in"><text><![CDATA[-]]></text></inputEntry>
                        <outputEntry id="out"><text><![CDATA[#{connectorServiceTask}]]></text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(key);
        dmnRepositoryService.createDeployment()
                .addString(key + ".dmn", unsafe)
                .tenantId(tenantProperties.id())
                .deploy();
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "safe"));

        assertThatThrownBy(() -> redeployDecisionTableUseCase.redeploy(ADMIN, key, 1))
                .isInstanceOf(InvalidDmnException.class);
        assertThat(deploymentAdapter.findDeploymentInfo(tenantProperties.id(), key))
                .get()
                .extracting(info -> info.version())
                .isEqualTo(2);
    }

    @Test
    void evaluate_noMatchingRule_returnsAnEmptyList() {
        String key = "itMiss" + System.nanoTime();
        deploymentAdapter.deploy(tenantProperties.id(), key + ".dmn", dmn(key, "ok"));

        DecisionEvaluationResult result =
                evaluateDecisionUseCase.evaluate(ADMIN, key, null, Map.of("customerCat", "GOLD"));

        assertThat(result.rows()).isEmpty();
    }

    private static byte[] dmn(String key, String output) {
        return DMN.formatted(key, output).getBytes(StandardCharsets.UTF_8);
    }
}
