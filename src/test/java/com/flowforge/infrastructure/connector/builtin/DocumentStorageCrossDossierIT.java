package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Issue #205 (TODO §101, D-STORAGE-REF-DOSSIER) — sur le vrai moteur, le titulaire d'une tâche placée avant « Appeler un
 * connecteur » {@code document-storage} pose {@code connectorInput} à sa complétion. Il ne doit ni lire ni effacer un
 * objet d'un autre dossier : pièce du dossier ou contenu stocké par le connecteur pour ce dossier-là.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DocumentStorageCrossDossierIT {

    private static final String TENANT = "tenant_dev";
    private static final byte[] SECRET = "%PDF-1.7 confidentiel\n".getBytes(StandardCharsets.US_ASCII);
    private static final AccessContext.User ACTOR = AccessContext.user("actor-1", Set.of("DOSSIER_MANAGER"));

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ConnectorInstanceRepository connectorInstances;

    @Autowired
    private DossierRepository dossiers;

    @Autowired
    private DossierElementRepository elements;

    @Autowired
    private DocumentStoragePort storage;

    @Autowired
    private CompleteTaskUseCase completeTaskUseCase;

    private String deploymentId;

    @BeforeEach
    void deploy() {
        UUID instanceId = connectorInstances.save(new ConnectorInstanceData(UUID.randomUUID(), TENANT,
                "Stockage " + UUID.randomUUID(), "document-storage", true, Map.of())).id();
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://flowforge/test">
                  <process id="storageCrossDossier" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="prepare" flowable:assignee="actor-1"/>
                    <serviceTask id="call" flowable:delegateExpression="${connectorServiceTask}"
                                 flowable:connectorInstanceId="%s"/>
                    <userTask id="again" flowable:assignee="actor-1"/>
                    <serviceTask id="callAgain" flowable:delegateExpression="${connectorServiceTask}"
                                 flowable:connectorInstanceId="%s"/>
                    <userTask id="done" flowable:assignee="actor-1"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="prepare"/>
                    <sequenceFlow id="f2" sourceRef="prepare" targetRef="call"/>
                    <sequenceFlow id="f3" sourceRef="call" targetRef="again"/>
                    <sequenceFlow id="f4" sourceRef="again" targetRef="callAgain"/>
                    <sequenceFlow id="f5" sourceRef="callAgain" targetRef="done"/>
                    <sequenceFlow id="f6" sourceRef="done" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(instanceId, instanceId);
        deploymentId = repositoryService.createDeployment().name("storage-cross-dossier.bpmn20.xml")
                .addString("storage-cross-dossier.bpmn20.xml", bpmn).deploy().getId();
    }

    @AfterEach
    void undeploy() {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    @Test
    void aCompletionCannotRetrieveAnotherDossiersElement() {
        String victimRef = victimElement();
        Dossier own = dossier();
        String pi = start(own);

        assertRefused(() -> complete(pi, "prepare", own, Map.of("operation", "RETRIEVE", "storageRef", victimRef)));
        assertThat(runtimeService.getVariable(pi, "connectorResult")).isNull();
    }

    @Test
    void aCompletionCannotDeleteAnotherDossiersElement() {
        String victimRef = victimElement();
        Dossier own = dossier();
        String pi = start(own);

        assertRefused(() -> complete(pi, "prepare", own, Map.of("operation", "DELETE", "storageRef", victimRef)));
        assertThat(storage.exists(TENANT, victimRef)).isTrue();
    }

    @Test
    void aCompletionCannotRetrieveContentTheConnectorStoredForAnotherDossier() {
        Dossier victim = dossier();
        String victimPi = start(victim);
        complete(victimPi, "prepare", victim, store());
        String victimRef = storageRef(victimPi);
        Dossier own = dossier();
        String pi = start(own);

        assertRefused(() -> complete(pi, "prepare", own, Map.of("operation", "RETRIEVE", "storageRef", victimRef)));
        assertRefused(() -> complete(pi, "prepare", own, Map.of("operation", "EXISTS", "storageRef", victimRef)));
    }

    @Test
    void theConnectorStillReadsAndDeletesWhatItStoredForTheSameDossier() {
        Dossier own = dossier();
        String pi = start(own);
        complete(pi, "prepare", own, store());
        String ref = storageRef(pi);

        complete(pi, "again", own, Map.of("operation", "RETRIEVE", "storageRef", ref));
        assertThat(connectorResult(pi)).containsEntry("content", Base64.getEncoder().encodeToString(SECRET));

        complete(pi, "done", own, Map.of());
        assertThat(storage.exists(TENANT, ref)).isTrue();
    }

    @Test
    void theConnectorDeletesWhatItStoredForTheSameDossier() {
        Dossier own = dossier();
        String pi = start(own);
        complete(pi, "prepare", own, store());
        String ref = storageRef(pi);

        complete(pi, "again", own, Map.of("operation", "DELETE", "storageRef", ref));

        assertThat(connectorResult(pi)).containsEntry("deleted", true);
        assertThat(storage.exists(TENANT, ref)).isFalse();
    }

    private static Map<String, Object> store() {
        return Map.of("operation", "STORE", "contentType", "application/pdf",
                "content", Base64.getEncoder().encodeToString(SECRET));
    }

    private static void assertRefused(Runnable completion) {
        assertThatThrownBy(completion::run).hasStackTraceContaining(
                "storageRef was not stored by the document-storage connector for this dossier");
    }

    private void complete(String pi, String taskKey, Dossier dossier, Map<String, Object> connectorInput) {
        String taskId = taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey(taskKey)
                .singleResult().getId();
        completeTaskUseCase.complete(taskId, dossier.id().toString(),
                connectorInput.isEmpty() ? Map.of() : Map.of("connectorInput", connectorInput), ACTOR);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> connectorResult(String pi) {
        return (Map<String, Object>) runtimeService.getVariable(pi, "connectorResult");
    }

    private String storageRef(String pi) {
        return (String) connectorResult(pi).get("storageRef");
    }

    private String start(Dossier dossier) {
        return runtimeService.startProcessInstanceByKey("storageCrossDossier",
                Map.of("dossierId", dossier.id().toString(), "tenantId", TENANT)).getId();
    }

    private String victimElement() {
        Dossier victim = dossier();
        String victimRef = storage.store(TENANT, "application/pdf", SECRET);
        elements.save(new DossierElement(UUID.randomUUID(), victim.id(), TENANT, ElementType.PRODUCED_DOC,
                "Décision confidentielle", victimRef, Map.of("contentType", "application/pdf"), "system", Instant.now(),
                "genererDecision"));
        return victimRef;
    }

    private Dossier dossier() {
        return dossiers.save(new Dossier(UUID.randomUUID(), TENANT, "REF-" + System.nanoTime(), "DEMANDE",
                "Stockage croisé", DossierStatus.OUVERT, Map.of(), null, 0L, "test", Instant.now(), null, null, null,
                null, null));
    }
}
