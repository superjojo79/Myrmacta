package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Issue #204 (TODO §100, D-DMN-RESERVED-OUTPUT) — sur le vrai moteur, une table DMN écrit une variable réservée. Chaque
 * table est déployée directement dans Flowable (le parseur l'aurait refusée) pour prouver l'effet, puis le parseur du
 * déploiement refuse le même document.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ReservedDmnOutputIT {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private DmnRepositoryService dmnRepositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private DossierRepository dossiers;

    @Autowired
    private DossierElementRepository elements;

    @Autowired
    private DocumentStoragePort storage;

    @Autowired
    private DmnParserPort dmnParser;

    private String bpmnDeploymentId;
    private String dmnDeploymentId;

    @AfterEach
    void undeploy() {
        if (bpmnDeploymentId != null) {
            repositoryService.deleteDeployment(bpmnDeploymentId, true);
        }
        if (dmnDeploymentId != null) {
            dmnRepositoryService.deleteDeployment(dmnDeploymentId);
        }
    }

    @Test
    void scenarioA_anOutputColumnLaundersAnotherDossiersDocument() {
        String victimRef = victimDocument();
        Dossier own = dossier();
        String ownRef = storage.store("tenant_dev", "application/pdf", PDF);
        String dmn = table("rdoChoice", "UNIQUE", "generatedDocumentId", "'" + victimRef + "'", "");
        deploy(dmn, "rdoChoice", "<serviceTask id=\"attach\" "
                + "flowable:delegateExpression=\"${attachGeneratedDocumentTask}\"/>");

        runtimeService.startProcessInstanceByKey("reservedDmnOutput",
                Map.of("dossierId", own.id().toString(), "generatedDocumentId", ownRef, "customerCat", "GOLD"));

        assertThat(elements.findByDossierIdOrderByAddedAtAsc(own.id()))
                .extracting(DossierElement::storageRef).containsExactly(victimRef);
        assertRefusedAtDeployment(dmn, "generatedDocumentId");
    }

    @Test
    void scenarioB_aMultiHitDecisionWritesItsResultsUnderTheDecisionId() {
        String dmn = table("connectorResult", "COLLECT", "result", "'forged'",
                "<rule><inputEntry id=\"in2\"><text>-</text></inputEntry>"
                        + "<outputEntry id=\"out2\"><text>'forged-too'</text></outputEntry></rule>");
        deploy(dmn, "connectorResult", "<userTask id=\"attach\"/>");

        String pi = runtimeService.startProcessInstanceByKey("reservedDmnOutput", Map.of("customerCat", "GOLD")).getId();

        assertThat(String.valueOf(runtimeService.getVariable(pi, "connectorResult")))
                .contains("forged", "forged-too");
        assertRefusedAtDeployment(dmn, "connectorResult");
    }

    @Test
    void scenarioC_aDecisionServiceWritesItsResultsUnderItsOwnId() {
        String dmn = table("rdoChoice", "COLLECT", "result", "'forged'",
                "<rule><inputEntry id=\"in2\"><text>-</text></inputEntry>"
                        + "<outputEntry id=\"out2\"><text>'forged-too'</text></outputEntry></rule>")
                .replace("</definitions>", "<decisionService id=\"dossierFields\" name=\"Service\">"
                        + "<outputDecision href=\"#rdoChoice\"/></decisionService></definitions>")
                .replace("http://www.omg.org/spec/DMN/20151101", "https://www.omg.org/spec/DMN/20191111/MODEL/");
        deploy(dmn, "dossierFields", "<userTask id=\"attach\"/>");

        String pi = runtimeService.startProcessInstanceByKey("reservedDmnOutput",
                Map.of("customerCat", "GOLD", "dossierFields", Map.of("montant", 100))).getId();

        assertThat(String.valueOf(runtimeService.getVariable(pi, "dossierFields")))
                .contains("forged", "forged-too").doesNotContain("montant");
        assertRefusedAtDeployment(dmn, "dossierFields");
    }

    private void assertRefusedAtDeployment(String dmn, String reserved) {
        assertThatThrownBy(() -> dmnParser.extractDecisionKey(dmn.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDmnException.class)
                .hasMessageContaining("DMN cannot write the reserved variable '" + reserved + "'");
    }

    /** start → décision → {@code next} → fin, la table et le process déployés sans passer par les parseurs FlowForge. */
    private void deploy(String dmn, String decisionKey, String next) {
        dmnDeploymentId = dmnRepositoryService.createDeployment().name("reserved-dmn-output.dmn")
                .addString("reserved-dmn-output.dmn", dmn).deploy().getId();
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://flowforge/test">
                  <process id="reservedDmnOutput" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="decide" flowable:type="dmn">
                      <extensionElements>
                        <flowable:field name="decisionTableReferenceKey"><flowable:string>%s</flowable:string></flowable:field>
                      </extensionElements>
                    </serviceTask>
                    %s
                    <endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="decide"/>
                    <sequenceFlow id="f2" sourceRef="decide" targetRef="attach"/>
                    <sequenceFlow id="f3" sourceRef="attach" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(decisionKey, next);
        bpmnDeploymentId = repositoryService.createDeployment().name("reserved-dmn-output.bpmn20.xml")
                .addString("reserved-dmn-output.bpmn20.xml", bpmn).deploy().getId();
    }

    private static String table(String decisionId, String hitPolicy, String output, String value, String moreRules) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def"
                             namespace="http://flowforge/test">
                  <decision id="%s" name="Choix">
                    <decisionTable id="dt" hitPolicy="%s">
                      <input><inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression></input>
                      <output id="o" name="%s" typeRef="string"/>
                      <rule>
                        <inputEntry id="in1"><text>"GOLD"</text></inputEntry>
                        <outputEntry id="out1"><text>%s</text></outputEntry>
                      </rule>
                      %s
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(decisionId, hitPolicy, output, value, moreRules);
    }

    private String victimDocument() {
        Dossier victim = dossier();
        String victimRef = storage.store("tenant_dev", "application/pdf", PDF);
        elements.save(new DossierElement(UUID.randomUUID(), victim.id(), "tenant_dev", ElementType.PRODUCED_DOC,
                "Décision confidentielle", victimRef, Map.of("contentType", "application/pdf"), "system", Instant.now(),
                "genererDecision"));
        return victimRef;
    }

    private Dossier dossier() {
        return dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", "REF-" + System.nanoTime(), "DEMANDE",
                "Sortie DMN réservée", DossierStatus.OUVERT, Map.of(), null, 0L, "test", Instant.now(), null, null, null,
                null, null));
    }
}
