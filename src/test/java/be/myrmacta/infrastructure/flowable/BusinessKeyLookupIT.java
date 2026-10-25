package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * D5 (TODO §79, revue) — la réconciliation retrouve par business key un process déjà TERMINÉ (historique), pour ne pas le
 * relancer quand la confirmation du lien avait échoué.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class BusinessKeyLookupIT {

    private static final String KEY = "businessKeyLookup";

    @Autowired
    private WorkflowEnginePort engine;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void aFinishedProcessIsStillFoundByItsBusinessKeyAndAnUnknownKeyIsNot() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://myrmacta/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow sourceRef="start" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY);
        String deployment = repositoryService.createDeployment().tenantId(tenantProperties.id())
                .addBytes(KEY + ".bpmn20.xml", xml.getBytes(StandardCharsets.UTF_8)).deploy().getId();
        try {
            String businessKey = UUID.randomUUID().toString();
            String instanceId = engine.start(KEY, tenantProperties.id(), businessKey, Map.of());

            assertThat(engine.findProcessInstanceIdByBusinessKey(businessKey)).contains(instanceId);
            assertThat(engine.findProcessInstanceIdByBusinessKey(UUID.randomUUID().toString())).isEmpty();
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }
}
