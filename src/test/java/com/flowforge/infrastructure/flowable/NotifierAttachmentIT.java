package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Issue #183 — « Notifier » avec pièce jointe sur le moteur réel : rattachement au dossier, outbox réelle, relais planifié,
 * {@link EmailPort} capturé (aucun serveur SMTP de test dans ce dépôt).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class NotifierAttachmentIT {

    private static final byte[] PDF = "%PDF-1.7\n% FlowForge\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private DossierRepository dossiers;

    @Autowired
    private DocumentStoragePort storage;

    @MockitoBean
    private EmailPort emailPort;

    private String deploymentId;

    @BeforeEach
    void deploy() {
        deploymentId = repositoryService.createDeployment()
                .name("notifier-attachment")
                .addClasspathResource("palette/notifier-attachment.bpmn20.xml")
                .deploy()
                .getId();
    }

    @AfterEach
    void undeploy() {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    @Test
    void theProducedDocumentAttachedToTheDossierIsSentWithTheEmail() {
        Dossier dossier = dossier();
        String ref = storage.store("tenant_dev", "application/pdf", PDF);
        String pi = runtimeService.startProcessInstanceByKey("notifierAttachment",
                Map.of("dossierId", dossier.id().toString(), "generatedDocumentId", ref)).getId();

        completeWaitingTask(pi, "naWait");

        ArgumentCaptor<EmailPort.EmailAttachmentContent> captor =
                ArgumentCaptor.forClass(EmailPort.EmailAttachmentContent.class);
        verify(emailPort, timeout(15_000)).send(eq("camille@example.org"), eq("Votre autorisation"),
                eq("Vous trouverez votre autorisation en pièce jointe."), captor.capture());
        assertThat(captor.getValue().fileName()).isEqualTo("autorisation.pdf");
        assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(captor.getValue().content()).isEqualTo(PDF);
    }

    @Test
    void aDocumentThatIsNotAnElementOfTheDossierIsRefusedAndNothingIsSent() {
        Dossier dossier = dossier();
        String ref = storage.store("tenant_dev", "application/pdf", PDF);
        String pi = runtimeService.startProcessInstanceByKey("notifierAttachmentNotAttached",
                Map.of("dossierId", dossier.id().toString(), "generatedDocumentId", ref)).getId();

        assertThatThrownBy(() -> completeWaitingTask(pi, "nnWait"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("notificationAttachment source variable:generatedDocumentId")
                .hasMessageNotContaining(ref);
        verify(emailPort, after(2_000).never()).send(anyString(), anyString(), anyString(), any());
        verify(emailPort, never()).send(anyString(), anyString(), anyString());
        assertThat(taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey("nnWait").count()).isEqualTo(1);
    }

    private Dossier dossier() {
        return dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", "REF-" + System.nanoTime(), "DEMANDE",
                "Notifier", DossierStatus.OUVERT, Map.of("email", "camille@example.org"), null, 0L, "test", Instant.now(),
                null, null, null, null, null));
    }

    private void completeWaitingTask(String processInstanceId, String key) {
        taskService.complete(taskService.createTaskQuery().processInstanceId(processInstanceId)
                .taskDefinitionKey(key).singleResult().getId());
    }
}
