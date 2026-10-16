package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.support.OidcClientTestConfiguration;
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

/** TODO §93 — « Notifier » avec {@code notificationTo="dossierField:email"} sur le moteur réel. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class NotifierRecipientFromDossierIT {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private DossierRepository dossiers;

    @MockitoBean
    private SendNotificationUseCase sendNotificationUseCase;

    private String deploymentId;

    @BeforeEach
    void deploy() {
        deploymentId = repositoryService.createDeployment()
                .name("notifier-dossier-field")
                .addClasspathResource("palette/notifier-dossier-field.bpmn20.xml")
                .deploy()
                .getId();
    }

    @AfterEach
    void undeploy() {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    private Dossier dossier(Map<String, Object> fields) {
        return dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", "REF-" + System.nanoTime(), "DEMANDE",
                "Notifier", DossierStatus.OUVERT, fields, null, 0L, "test", Instant.now(), null, null, null, null, null));
    }

    private void completeWaitingTask(String processInstanceId) {
        taskService.complete(taskService.createTaskQuery().processInstanceId(processInstanceId)
                .taskDefinitionKey("ndWait").singleResult().getId());
    }

    @Test
    void theAddressIsReadFromTheDossierAsItIsWhenTheBlockRuns() {
        Dossier dossier = dossier(Map.of("email", "ancienne@example.org"));
        String pi = runtimeService.startProcessInstanceByKey("notifierDossierField",
                Map.of("dossierId", dossier.id().toString())).getId();
        // L'adresse est corrigée après le démarrage : l'instantané dossierFields n'est pas utilisé.
        dossiers.save(dossiers.findById(dossier.id()).orElseThrow().withFields(Map.of("email", "camille@example.org")));

        completeWaitingTask(pi);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sendNotificationUseCase).send(captor.capture(), eq("tenant_dev"));
        assertThat(captor.getValue().to()).isEqualTo("camille@example.org");
        assertThat(taskService.createTaskQuery().processInstanceId(pi).singleResult().getTaskDefinitionKey())
                .isEqualTo("ndDone");
    }

    @Test
    void anEmptyFieldMakesTheBlockFailAndNothingIsSent() {
        Dossier dossier = dossier(Map.of("email", ""));
        String pi = runtimeService.startProcessInstanceByKey("notifierDossierField",
                Map.of("dossierId", dossier.id().toString())).getId();

        assertThatThrownBy(() -> completeWaitingTask(pi))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("dossierField:email");
        verify(sendNotificationUseCase, never()).send(any(), any());
        assertThat(taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey("ndWait").count()).isEqualTo(1);
    }
}
