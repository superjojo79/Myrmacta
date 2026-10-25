package be.myrmacta.infrastructure.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** TODO §68 — émission, envoi et invalidation sur le vrai moteur Flowable (T-ISSUE, T-SEND, T-INVAL). */
@ExtendWith(OutputCaptureExtension.class)
class PortalLinkIssuanceEngineTest extends AbstractPortalEngineTest {

    @Autowired
    private PortalTokenPort tokenPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        ensureSchema();
    }

    @Test
    void externalTaskCreation_issuesPendingLink_thenInviteActivatesIt(CapturedOutput output) {
        // T-ISSUE-1, T-SEND-1, T-SEND-3
        String recipient = "tiers-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("contactEmail", recipient)).id();

        Task task = startPortalSmoke(dossierId);

        PortalLink pending = portalLinkRepository.findLiveByTaskId(task.getId()).getFirst();
        assertThat(pending.status()).isEqualTo(PortalLinkStatus.PENDING_SEND);
        assertThat(pending.recipientEmail()).isEqualTo(recipient);
        assertThat(pending.dossierId()).isEqualTo(dossierId);
        assertThat(pending.ttlDays()).isEqualTo(7);

        String token = sendAndCaptureToken(recipient);

        verify(emailPort).send(eq(recipient), contains("Avis du tiers"),
                contains("https://ff.example.org/tenant-a/portal/#" + token));
        PortalLink active = portalLinkRepository.findById(pending.id()).orElseThrow();
        assertThat(active.status()).isEqualTo(PortalLinkStatus.ACTIVE);
        assertThat(active.tokenHash()).isEqualTo(tokenPort.hash(token));
        // T-SEND-3 : le jeton en clair n'existe ni en base, ni dans les logs.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from portal_link");
        assertThat(rows.toString()).doesNotContain(token);
        List<Map<String, Object>> outbox = jdbcTemplate.queryForList("select * from notification_outbox");
        assertThat(outbox.toString()).doesNotContain(token);
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(recipient);
    }

    @Test
    void missingAddress_issuesNotSentLink_andProcessKeepsRunning() {
        // T-ISSUE-2 : pas d'incident moteur
        UUID dossierId = dossier(Map.of()).id();

        Task task = startPortalSmoke(dossierId);

        assertThat(task).isNotNull();
        PortalLink link = portalLinkRepository.findLiveByTaskId(task.getId()).getFirst();
        assertThat(link.status()).isEqualTo(PortalLinkStatus.NOT_SENT);
        assertThat(link.statusReason()).isEqualTo(PortalLinkStatusReason.NO_ADDRESS);
    }

    @Test
    void internalCompletion_revokesTheLiveLink() {
        // T-INVAL-1
        UUID dossierId = dossier(Map.of("contactEmail", "a" + System.nanoTime() + "@example.org")).id();
        Task task = startPortalSmoke(dossierId);
        UUID linkId = portalLinkRepository.findLiveByTaskId(task.getId()).getFirst().id();

        taskService.complete(task.getId());

        PortalLink link = portalLinkRepository.findById(linkId).orElseThrow();
        assertThat(link.status()).isEqualTo(PortalLinkStatus.REVOKED);
        assertThat(link.statusReason()).isEqualTo(PortalLinkStatusReason.TASK_GONE);
    }

    @Test
    void processCancellation_revokesTheLiveLink() {
        // T-INVAL-2
        UUID dossierId = dossier(Map.of("contactEmail", "b" + System.nanoTime() + "@example.org")).id();
        Task task = startPortalSmoke(dossierId);
        UUID linkId = portalLinkRepository.findLiveByTaskId(task.getId()).getFirst().id();

        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), "annulé");

        assertThat(portalLinkRepository.findById(linkId).orElseThrow().status()).isEqualTo(PortalLinkStatus.REVOKED);
    }

    @Test
    void rolledBackTaskCreation_leavesNoOrphanLink() {
        // T-ISSUE-8
        UUID dossierId = dossier(Map.of("contactEmail", "c" + System.nanoTime() + "@example.org")).id();

        transactionTemplate.executeWithoutResult(status -> {
            startPortalSmoke(dossierId);
            status.setRollbackOnly();
        });

        assertThat(portalLinkRepository.findByDossierId(dossierId)).isEmpty();
    }

    @Test
    void internalTask_issuesNoLink() {
        // T-ISSUE-6 : tâche user-question interne (processus livré)
        String processInstanceId = runtimeService.startProcessInstanceByKeyAndTenantId(
                "userQuestionSmoke", "uq-" + UUID.randomUUID(), "tenant_dev").getId();
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

        assertThat(portalLinkRepository.findLiveByTaskId(task.getId())).isEmpty();
        verify(emailPort, org.mockito.Mockito.never()).send(anyString(), anyString(), contains(task.getId()));
    }
}
