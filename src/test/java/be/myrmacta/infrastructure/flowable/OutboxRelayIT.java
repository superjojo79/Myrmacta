package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.TriggerOutboxPayload;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
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
class OutboxRelayIT {

    @Autowired
    private OutboxRelayScheduler relayScheduler;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private WorkflowTypeConfigRepository configRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private GetDossierWorkflowHistoryUseCase historyUseCase;

    @Test
    void pendingEvent_isStartedConfirmedAndMarkedProcessed() {
        String processKey = "smokeProcess";
        ensureConfig(processKey);
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);

        var event = outboxRepository.savePending(new TriggerOutboxPayload(
                dossier.id(), null, processKey, Map.of("source", "it"), "seed:it"));

        relayScheduler.processOne(event);

        var history = historyUseCase.getHistory(dossier.id(), PageQuery.defaults()).content();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().isActive()).isTrue();
        assertThat(history.getFirst().processInstanceId()).isNotBlank();
        assertThat(outboxRepository.lockNextPending(50))
                .extracting(e -> e.eventId())
                .doesNotContain(event.eventId());
    }

    @Test
    void unknownProcessKey_leavesPendingUntilMaxAttemptsThenDisappearsFromPending() {
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);
        var event = outboxRepository.savePending(new TriggerOutboxPayload(
                dossier.id(), null, "missingProcess-" + UUID.randomUUID(), Map.of(), "seed:bad"));

        relayScheduler.processOne(event);
        assertThat(outboxRepository.lockNextPending(50))
                .extracting(e -> e.eventId())
                .contains(event.eventId());

        for (int i = 0; i < 4; i++) {
            relayScheduler.processOne(event);
        }

        assertThat(outboxRepository.lockNextPending(50))
                .extracting(e -> e.eventId())
                .doesNotContain(event.eventId());
        assertThat(historyUseCase.getHistory(dossier.id(), PageQuery.defaults()).content()).isEmpty();
    }

    private void ensureConfig(String workflowKey) {
        if (configRepository.findByWorkflowKey("tenant_dev", workflowKey).isPresent()) {
            return;
        }
        configRepository.save(new WorkflowTypeConfig(
                UUID.randomUUID(),
                "tenant_dev",
                workflowKey,
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("COMPLETED", "Completed", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS, DossierStatus.SUSPENDU),
                null,
                List.of()));
    }

    private static Dossier dossier(DossierStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-02T12:00:00Z");
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-OBX-" + id.toString().replace("-", "").substring(0, 12),
                "WF",
                "Outbox dossier",
                status,
                Map.of(),
                null,
                0,
                "creator",
                now,
                "creator",
                now,
                null,
                null,
                null);
    }
}
