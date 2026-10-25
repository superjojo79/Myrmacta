package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * Issue #201 (D-DOC-GENERATED-RESERVED) — une tâche s'intercale entre la génération et « Joindre le document » : sa
 * complétion ne doit pas pouvoir remplacer {@code generatedDocumentId} par la référence du document d'un autre dossier, qui
 * deviendrait un {@code PRODUCED_DOC} légitime du dossier courant (joignable à un e-mail, signable).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class GeneratedDocumentLaunderingIT {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

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

    private String deploymentId;

    @BeforeEach
    void deploy() {
        deploymentId = repositoryService.createDeployment()
                .name("attach-after-user-task")
                .addClasspathResource("palette/attach-after-user-task.bpmn20.xml")
                .deploy()
                .getId();
    }

    @AfterEach
    void undeploy() {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    @Test
    void aTaskCompletionCannotReplaceTheGeneratedDocumentByAnotherDossiersDocument() {
        Dossier victim = dossier();
        String victimRef = storage.store("tenant_dev", "application/pdf", PDF);
        elements.save(new DossierElement(UUID.randomUUID(), victim.id(), "tenant_dev", ElementType.PRODUCED_DOC,
                "Décision confidentielle", victimRef, Map.of("contentType", "application/pdf"), "system", Instant.now(),
                "genererDecision"));

        Dossier own = dossier();
        String ownRef = storage.store("tenant_dev", "application/pdf", PDF);
        String pi = runtimeService.startProcessInstanceByKey("attachAfterUserTask",
                Map.of("dossierId", own.id().toString(), "generatedDocumentId", ownRef)).getId();
        String taskId = taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey("aaReview")
                .singleResult().getId();

        assertThatThrownBy(() -> completeTaskUseCase.complete(taskId, own.id().toString(),
                Map.of("generatedDocumentId", victimRef), AccessContext.user("actor-1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generatedDocumentId");
        assertThat(elements.findByDossierIdOrderByAddedAtAsc(own.id()))
                .extracting(DossierElement::storageRef).doesNotContain(victimRef);

        // Sans variable forgée, le document réellement généré est rattaché.
        completeTaskUseCase.complete(taskId, own.id().toString(), Map.of(),
                AccessContext.user("actor-1", Set.of("DOSSIER_MANAGER")));
        assertThat(elements.findByDossierIdOrderByAddedAtAsc(own.id()))
                .extracting(DossierElement::storageRef).containsExactly(ownRef);
    }

    private Dossier dossier() {
        return dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", "REF-" + System.nanoTime(), "DEMANDE",
                "Blanchiment", DossierStatus.OUVERT, Map.of(), null, 0L, "test", Instant.now(), null, null, null, null,
                null));
    }
}
