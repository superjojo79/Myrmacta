package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.model.OutboxStatus;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class WorkflowPersistenceTest {

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private WorkflowTypeConfigRepository configRepository;

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private AttachWorkflowUseCase attachWorkflow;

    @Autowired
    private GetDossierWorkflowHistoryUseCase historyUseCase;

    @Test
    void persistsWorkflowConfigAndIdempotencyRows() {
        WorkflowTypeConfig config = smokeConfig("workflow-config-" + UUID.randomUUID());

        configRepository.save(config);
        idempotencyStore.remember("tenant_dev", UUID.randomUUID(), "idem-1", UUID.randomUUID());

        assertThat(configRepository.findByWorkflowKey("tenant_dev", config.workflowKey()))
                .contains(config);
    }

    @Test
    void outboxRepositoryStoresAndMarksEvents() {
        var payload = new TriggerOutboxPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "smokeProcess",
                Map.of("priority", "NORMAL"),
                "trigger-1");

        var event = outboxRepository.savePending(payload);
        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);

        assertThat(outboxRepository.lockNextPending(10))
                .extracting(stored -> stored.eventId())
                .contains(event.eventId());

        outboxRepository.markProcessed(event.eventId());

        assertThat(outboxRepository.lockNextPending(10))
                .extracting(stored -> stored.eventId())
                .doesNotContain(event.eventId());
    }

    @Test
    void reserveConfirmAndAbortMaintainDossierCurrentWorkflowLink() {
        String processKey = "smokeProcess-" + UUID.randomUUID();
        configRepository.save(smokeConfig(processKey));
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);

        var reserved = attachWorkflow.reserve(dossier.id(), processKey, "manual");
        assertThat(reserved.isReserved()).isTrue();
        assertThat(dossierRepository.findById(dossier.id()).orElseThrow().currentWorkflowLinkId())
                .isNull();

        attachWorkflow.confirmStarted(reserved.id(), "proc-1");
        assertThat(dossierRepository.findById(dossier.id()).orElseThrow().currentWorkflowLinkId())
                .isEqualTo(reserved.id());

        assertThatThrownBy(() -> attachWorkflow.reserve(dossier.id(), processKey, "manual-2"))
                .isInstanceOf(WorkflowAlreadyRunningException.class);

        assertThat(historyUseCase.getHistory(dossier.id()))
                .hasSize(1)
                .first()
                .extracting(link -> link.sequence())
                .isEqualTo(1);
    }

    private static WorkflowTypeConfig smokeConfig(String workflowKey) {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                "tenant_dev",
                workflowKey,
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("COMPLETED", "Completed", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS, DossierStatus.SUSPENDU),
                null);
    }

    private static Dossier dossier(DossierStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-WF-" + id.toString().replace("-", "").substring(0, 12),
                "WF",
                "Workflow dossier",
                status,
                Map.of(),
                null,
                0,
                "creator",
                now,
                "creator",
                now,
                null,
                null);
    }
}
