package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.in.GenerateDocumentUseCase.GenerateDocumentCommand;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** S-RT-7 — un process à briques en attributs s'exécute de bout en bout sans variable de paramètre (TODO §73). */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class BlockAttributesEndToEndIT {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    private String deploymentId;

    // Revue M8 : le process de test n'est plus dans `processes/` (auto-déployé dans tous les contextes Spring de la
    // suite) ; il est déployé ici, pour ce test seulement, puis retiré.
    @BeforeEach
    void deploySmokeProcess() {
        deploymentId = repositoryService.createDeployment()
                .name("block-attributes-smoke")
                .addClasspathResource("palette/block-attributes-smoke.bpmn20.xml")
                .deploy()
                .getId();
    }

    @AfterEach
    void removeSmokeProcess() {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    @Autowired
    private TaskService taskService;

    @Autowired
    private DossierRepository dossiers;

    @MockitoBean
    private GenerateDocumentUseCase generateDocumentUseCase;

    @MockitoBean
    private DocumentStoragePort documentStoragePort;

    @Test
    void literalAttributesDriveTheRealEngineWithoutAnyParameterVariable() {
        when(generateDocumentUseCase.generate(any())).thenReturn("%PDF".getBytes());
        when(documentStoragePort.store(eq("tenant_dev"), eq("application/pdf"), any())).thenReturn("tenant_dev/x.pdf");
        Instant now = Instant.now();
        Dossier dossier = dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", "REF-" + System.nanoTime(),
                "SINISTRE", "Brique", DossierStatus.OUVERT, Map.of(), null, 0L, "test", now, null, null, null, null,
                null));

        Map<String, Object> variables = new HashMap<>();
        variables.put("dossierId", dossier.id().toString());
        String processInstanceId = runtimeService.startProcessInstanceByKey("blockAttributesSmoke", variables).getId();

        verify(generateDocumentUseCase).generate(new GenerateDocumentCommand("invoice", "premium", "fr",
                Map.of("dossierId", dossier.id().toString())));
        assertThat(dossiers.findById(dossier.id()).orElseThrow().status()).isEqualTo(DossierStatus.EN_COURS);
        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult().getName())
                .isEqualTo("Terminé");
        assertThat(runtimeService.getVariable(processInstanceId, "generatedDocumentId")).isEqualTo("tenant_dev/x.pdf");
    }
}
