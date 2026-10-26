package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §61 M3, Phase 7 gap : miroir de {@code FlowableWorkflowDeploymentAdapterTest}, jamais
 * couvert par un test dédié avant la revue fichier par fichier. {@code findAllLatestDeployments}
 * en particulier n'avait aucune couverture sur le vrai moteur (seul {@code ListDecisionTablesTool}
 * l'exerçait, avec le port mocké).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class FlowableDmnDeploymentAdapterTest {

    private static final String DMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                         namespace="http://myrmacta/test">
              <decision id="%s" name="Def">
                <decisionTable id="dt" hitPolicy="UNIQUE">
                  <input>
                    <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                  </input>
                  <output id="o" name="result" typeRef="string" />
                  <rule>
                    <inputEntry id="in"><text>"BRONZE"</text></inputEntry>
                    <outputEntry id="out"><text>'ok'</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private FlowableDmnDeploymentAdapter adapter;

    @Test
    void deploy_newDecision_createsFirstVersion() {
        String key = "adapterDeployNew" + System.nanoTime();

        DecisionTableDeploymentResult result = adapter.deploy(tenantProperties.id(), "table.dmn", dmn(key));

        assertThat(result.decisionKey()).isEqualTo(key);
        assertThat(result.deployedVersion()).isEqualTo(1);
        assertThat(result.deploymentId()).isNotBlank();
    }

    @Test
    void deploy_nullFilename_doesNotThrowAndDeploysWithDefaultResourceName() {
        String key = "adapterNullFilename" + System.nanoTime();

        DecisionTableDeploymentResult result = adapter.deploy(tenantProperties.id(), null, dmn(key));

        assertThat(result.decisionKey()).isEqualTo(key);
        assertThat(result.deployedVersion()).isEqualTo(1);
    }

    @Test
    void deploy_sameKeyTwice_incrementsVersion() {
        String key = "adapterRedeploy" + System.nanoTime();
        adapter.deploy(tenantProperties.id(), "table.dmn", dmn(key));

        DecisionTableDeploymentResult second = adapter.deploy(tenantProperties.id(), "table.dmn", dmn(key));

        assertThat(second.deployedVersion()).isEqualTo(2);
    }

    @Test
    void findDeploymentInfo_afterDeploy_returnsLatestVersion() {
        String key = "adapterInfo" + System.nanoTime();
        adapter.deploy(tenantProperties.id(), "table.dmn", dmn(key));

        Optional<DecisionTableDeploymentInfo> info = adapter.findDeploymentInfo(tenantProperties.id(), key);

        assertThat(info).contains(new DecisionTableDeploymentInfo(key, 1));
    }

    @Test
    void findDeploymentInfo_keyNeverDeployed_returnsEmpty() {
        Optional<DecisionTableDeploymentInfo> info =
                adapter.findDeploymentInfo(tenantProperties.id(), "neverDeployedKey" + System.nanoTime());

        assertThat(info).isEmpty();
    }

    @Test
    void findAllLatestDeployments_includesEachDeployedKeyAtItsLatestVersionOnly() {
        String keyA = "adapterListA" + System.nanoTime();
        String keyB = "adapterListB" + System.nanoTime();
        adapter.deploy(tenantProperties.id(), "table.dmn", dmn(keyA));
        adapter.deploy(tenantProperties.id(), "table.dmn", dmn(keyB));
        adapter.deploy(tenantProperties.id(), "table.dmn", dmn(keyB)); // keyB now at version 2

        var all = adapter.findAllLatestDeployments(tenantProperties.id());

        assertThat(all).contains(new DecisionTableDeploymentInfo(keyA, 1), new DecisionTableDeploymentInfo(keyB, 2));
        assertThat(all).filteredOn(info -> info.decisionKey().equals(keyB)).hasSize(1);
    }

    @Test
    void findDeployedXml_deployedKey_returnsLatestVersionXml() {
        String key = "adapterXml" + System.nanoTime();
        adapter.deploy(tenantProperties.id(), "table.dmn", dmn(key));

        Optional<byte[]> xml = adapter.findDeployedXml(tenantProperties.id(), key);

        assertThat(xml).isPresent();
        assertThat(new String(xml.get(), StandardCharsets.UTF_8)).contains("id=\"" + key + "\"");
    }

    @Test
    void findDeployedXml_keyNeverDeployed_returnsEmpty() {
        Optional<byte[]> xml =
                adapter.findDeployedXml(tenantProperties.id(), "neverDeployedXmlKey" + System.nanoTime());

        assertThat(xml).isEmpty();
    }

    private static byte[] dmn(String key) {
        return DMN.formatted(key).getBytes(StandardCharsets.UTF_8);
    }
}
