package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.support.OidcClientTestConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Issue #203 (TODO §99, D-BPMN-RESERVED-WRITE) — les deux scénarios de l'issue, sur le vrai moteur. Chaque BPMN est déployé
 * directement dans Flowable (le parseur l'aurait refusé) pour prouver l'effet : une complétion qui ne pose aucune variable
 * réservée suffit à rattacher le document d'un autre dossier. Le parseur du déploiement refuse ensuite le même document.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ReservedVariableWriteIT {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final AccessContext.User ACTOR = AccessContext.user("actor-1", Set.of("DOSSIER_MANAGER"));

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private DossierRepository dossiers;

    @Autowired
    private DossierElementRepository elements;

    @Autowired
    private DocumentStoragePort storage;

    @Autowired
    private CompleteTaskUseCase completeTaskUseCase;

    @Autowired
    private BpmnParserPort bpmnParser;

    private String deploymentId;

    @AfterEach
    void undeploy() {
        if (deploymentId != null) {
            repositoryService.deleteDeployment(deploymentId, true);
        }
    }

    @Test
    void scenarioA_aMultiInstanceElementVariableLaundersAnotherDossiersDocument() throws IOException {
        String resource = "palette/reserved-write-multi-instance.bpmn20.xml";
        String victimRef = victimDocument();
        Dossier own = dossier();
        String ownRef = storage.store("tenant_dev", "application/pdf", PDF);
        deploy(resource);
        String pi = runtimeService.startProcessInstanceByKey("reservedWriteMultiInstance",
                Map.of("dossierId", own.id().toString(), "generatedDocumentId", ownRef)).getId();

        completeTaskUseCase.complete(task(pi, "rwmReview"), own.id().toString(), Map.of("refs", List.of(victimRef)), ACTOR);

        assertThat(elements.findByDossierIdOrderByAddedAtAsc(own.id()))
                .extracting(DossierElement::storageRef).containsExactly(victimRef);
        assertRefusedAtDeployment(resource, "generatedDocumentId");
    }

    @Test
    void scenarioB_aCallActivityInputMappingLaundersAnotherDossiersDocument() throws IOException {
        String resource = "palette/reserved-write-call-activity.bpmn20.xml";
        String victimRef = victimDocument();
        Dossier own = dossier();
        deploy(resource);
        String pi = runtimeService.startProcessInstanceByKey("reservedWriteCallActivity",
                Map.of("dossierId", own.id().toString(), "tenantId", "tenant_dev")).getId();

        completeTaskUseCase.complete(task(pi, "rwcReview"), own.id().toString(), Map.of("docRef", victimRef), ACTOR);

        assertThat(elements.findByDossierIdOrderByAddedAtAsc(own.id()))
                .extracting(DossierElement::storageRef).containsExactly(victimRef);
        assertRefusedAtDeployment(resource, "generatedDocumentId");
    }

    private void assertRefusedAtDeployment(String resource, String reserved) throws IOException {
        byte[] xml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            xml = in.readAllBytes();
        }
        assertThatThrownBy(() -> bpmnParser.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("BPMN cannot write the reserved variable '" + reserved + "'");
    }

    private void deploy(String resource) {
        deploymentId = repositoryService.createDeployment().name(resource).addClasspathResource(resource).deploy().getId();
    }

    private String task(String processInstanceId, String key) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).taskDefinitionKey(key)
                .singleResult().getId();
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
                "Recopie réservée", DossierStatus.OUVERT, Map.of(), null, 0L, "test", Instant.now(), null, null, null,
                null, null));
    }
}
