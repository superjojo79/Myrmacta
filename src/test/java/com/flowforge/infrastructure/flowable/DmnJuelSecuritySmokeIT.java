package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import org.flowable.dmn.api.DecisionExecutionAuditContainer;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.DmnDeployment;
import org.flowable.dmn.api.DmnRepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §61 (introduction-dmn) — preuve d'exécution réelle, pas seulement une lecture de code
 * (décompilation bytecode de {@code flowable-dmn-engine}, voir Javadoc {@link
 * FlowableDmnParserAdapter}), que le moteur DMN de Flowable évalue les cellules via le même
 * résolveur JUEL que le moteur BPMN — jusqu'à résoudre un bean Spring vivant du contexte
 * applicatif pour un identifiant non quoté, exactement la vulnérabilité H1-bis (TODO §57/§58).
 *
 * <p>Déploiement <b>direct</b> via {@link DmnRepositoryService}, en contournant délibérément
 * {@link FlowableDmnParserAdapter} — ce test ne prouve pas que FlowForge est vulnérable (l'adaptateur
 * rejette déjà ces deux tables, voir {@link FlowableDmnParserAdapterTest}), il prouve que le risque
 * que l'adaptateur neutralise est réel sur le moteur réel, pas hypothétique.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DmnJuelSecuritySmokeIT {

    @Autowired
    private DmnRepositoryService dmnRepositoryService;

    @Autowired
    private DmnDecisionService dmnDecisionService;

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void bareIdentifierMatchingASpringBeanName_resolvesToTheLiveBean() {
        String key = "juelGapBean" + System.nanoTime();
        // "-" input entry = DMN's own "any" wildcard, matches unconditionally without ever
        // reaching the EL evaluator (RuleEngineExecutorImpl shortcut) -- isolates the proof to the
        // output entry, which carries the bare identifier under test.
        deploy(key, "connectorServiceTask");

        // executeWithSingleResult() swallows the underlying failure and returns null (verified:
        // an earlier version of this test asserting on the return value could not distinguish the
        // two cases at all). executeWithAuditTrail() is the API Flowable itself provides to inspect
        // what actually happened during evaluation -- and it shows that "#{connectorServiceTask}"
        // resolved to the actual live Spring bean registered under that name in this application's
        // context, only failing afterwards because its variable factory cannot box an arbitrary
        // POJO as a DMN variable. The failure message names the resolved class and object identity
        // ("...ConnectorServiceTask@..."), which is direct proof of resolution, not of failure to
        // resolve. This is the H1-bis vulnerability class, confirmed on the real DMN engine rather
        // than assumed from reading FlowableDmnParserAdapter's Javadoc.
        DecisionExecutionAuditContainer audit = dmnDecisionService.createExecuteDecisionBuilder()
                .decisionKey(key)
                .tenantId(tenantProperties.id())
                .variable("customerCat", "irrelevant")
                .executeWithAuditTrail();

        assertThat(audit.isFailed()).isTrue();
        // getExceptionMessage()/getException() only carry the short top-level message ("could not
        // create result variable") -- the engine's own log line at the point of failure is where
        // the resolved object surfaces, observed verbatim on a real run of this exact test:
        // "could not create result variable: type null expression result
        //  com.flowforge.infrastructure.flowable.ConnectorServiceTask@337b0d6b"
        // (org.flowable.dmn.engine.impl.el.ExecutionVariableFactory, ERROR level). That log line is
        // the definitive proof kept in this Javadoc/comment rather than re-asserted here, since the
        // audit API does not expose it as structured data.
        assertThat(audit.getExceptionMessage()).contains("could not create result variable");
    }

    @Test
    void bareIdentifierNotMatchingAnyBeanOrVariable_failsDifferently_controlCase() {
        String key = "juelGapControl" + System.nanoTime();
        deploy(key, "definitelyNotARealBeanOrVariable12345");

        // Contrast with the case above: an identifier that matches neither a process/execution
        // variable nor a Spring bean fails with a distinct "unknown property" error -- not the
        // same failure as resolving to a live object. That difference in failure mode is exactly
        // what makes the bean-name case dangerous: it is not a generic authoring typo, it reaches
        // and constructs a real application object.
        DecisionExecutionAuditContainer audit = dmnDecisionService.createExecuteDecisionBuilder()
                .decisionKey(key)
                .tenantId(tenantProperties.id())
                .variable("customerCat", "irrelevant")
                .executeWithAuditTrail();

        assertThat(audit.isFailed()).isTrue();
        assertThat(audit.getExceptionMessage()).contains("Cannot resolve identifier");
        assertThat(audit.getExceptionMessage()).contains("definitelyNotARealBeanOrVariable12345");
    }

    private void deploy(String decisionKey, String bareOutputEntryText) {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def_%1$s" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="%1$s" name="Gap">
                    <decisionTable id="dt" hitPolicy="UNIQUE">
                      <input>
                        <inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression>
                      </input>
                      <output id="o" name="result" />
                      <rule>
                        <inputEntry id="in"><text><![CDATA[-]]></text></inputEntry>
                        <outputEntry id="out"><text><![CDATA[%2$s]]></text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """
                .formatted(decisionKey, bareOutputEntryText);

        DmnDeployment deployment = dmnRepositoryService
                .createDeployment()
                .addString(decisionKey + ".dmn", xml)
                .tenantId(tenantProperties.id())
                .deploy();
        assertThat(deployment).isNotNull();
    }
}
