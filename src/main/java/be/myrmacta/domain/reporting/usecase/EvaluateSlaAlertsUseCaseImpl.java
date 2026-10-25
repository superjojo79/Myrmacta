package be.myrmacta.domain.reporting.usecase;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.reporting.UnitOfWork;
import be.myrmacta.domain.reporting.model.SlaAlertCandidate;
import be.myrmacta.domain.reporting.model.SlaAlertState;
import be.myrmacta.domain.reporting.model.SlaClock;
import be.myrmacta.domain.reporting.model.SlaStatus;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.port.in.EvaluateSlaAlertsUseCase;
import be.myrmacta.domain.reporting.port.out.SlaAlertCandidatePort;
import be.myrmacta.domain.reporting.port.out.SlaAlertStateRepository;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EvaluateSlaAlertsUseCaseImpl implements EvaluateSlaAlertsUseCase {

    private final TenantSlaSettingsRepository settingsRepository;
    private final SlaAlertCandidatePort candidatePort;
    private final SlaAlertStateRepository stateRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final NotificationWebhookRepository webhookRepository;
    private final DossierWorkflowLinkRepository workflowLinkRepository;
    private final WorkflowParticipantPort participantPort;

    public EvaluateSlaAlertsUseCaseImpl(
            TenantSlaSettingsRepository settingsRepository,
            SlaAlertCandidatePort candidatePort,
            SlaAlertStateRepository stateRepository,
            SendNotificationUseCase sendNotificationUseCase,
            NotificationWebhookRepository webhookRepository,
            DossierWorkflowLinkRepository workflowLinkRepository,
            WorkflowParticipantPort participantPort) {
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.candidatePort = Objects.requireNonNull(candidatePort, "candidatePort");
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.sendNotificationUseCase = Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository");
        this.workflowLinkRepository = Objects.requireNonNull(workflowLinkRepository, "workflowLinkRepository");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
    }

    @Override
    public int scan(String tenantId, Instant now, UnitOfWork unitOfWork) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(unitOfWork, "unitOfWork");
        Optional<TenantSlaSettings> found = settingsRepository.findByTenantId(tenantId);
        if (found.isEmpty()) {
            return 0;
        }
        TenantSlaSettings settings = found.get();
        if (!settings.alertingEnabled()) {
            return 0;
        }
        if (settings.lastAlertScanAt() != null) {
            Instant due = settings.lastAlertScanAt().plus(Duration.ofMinutes(settings.alertIntervalMinutes()));
            if (now.isBefore(due)) {
                return 0;
            }
        }

        List<UUID> enabledWebhooks = enabledWebhooks(settings);
        int failures = 0;
        for (SlaAlertCandidate candidate : candidatePort.listCandidates(tenantId)) {
            try {
                unitOfWork.run(() -> evaluateOne(settings, candidate, now, enabledWebhooks));
            } catch (RuntimeException ex) {
                failures++;
            }
        }
        settingsRepository.advanceLastAlertScanAt(settings.tenantId(), now);
        return failures;
    }

    private void evaluateOne(
            TenantSlaSettings settings, SlaAlertCandidate candidate, Instant now, List<UUID> enabledWebhooks) {
        if (candidate.slaTargetHours() == null) {
            return;
        }
        SlaStatus status = SlaStatus.of(
                SlaClock.elapsed(
                        candidate.createdAt(),
                        candidate.closedAt(),
                        candidate.updatedAt(),
                        now,
                        candidate.closed()),
                candidate.slaTargetHours(),
                candidate.closed());
        if (status == null) {
            return;
        }
        Optional<SlaAlertState> previous = stateRepository.find(settings.tenantId(), candidate.id());
        if (previous.isPresent() && previous.get().lastStatus() == status) {
            return;
        }
        SlaAlertState next = new SlaAlertState(settings.tenantId(), candidate.id(), status, now);
        // TODO §79 (D5) : la transition d'état est « prise » AVANT de notifier. Deux exécutants concurrents voient le même
        // changement ; un seul gagne le compare-and-set (ou l'insertion, protégée par la clé primaire : le perdant échoue et
        // son unité de travail est annulée, outbox comprise). Sinon chacun mettait l'alerte (e-mail, webhook, navigateur) en file.
        if (previous.isPresent()) {
            if (!stateRepository.transition(previous.get(), next)) {
                return;
            }
        } else if (!stateRepository.insertIfAbsent(next)) {
            return; // un autre exécutant a évalué ce dossier en premier (insertion stricte, pas de fusion)
        }
        if (status == SlaStatus.AT_RISK || status == SlaStatus.BREACHED) {
            notify(settings, candidate, status, enabledWebhooks);
        }
    }

    private void notify(
            TenantSlaSettings settings, SlaAlertCandidate candidate, SlaStatus status, List<UUID> enabledWebhooks) {
        String subject = "SLA " + status.name() + " — " + candidate.reference();
        String body = "Le dossier "
                + candidate.reference()
                + " ("
                + candidate.dossierTypeCode()
                + ") est passé au statut "
                + status.name()
                + ".";
        Map<String, Object> payload = payload(candidate, status, subject, body);
        for (String email : settings.alertEmails()) {
            sendNotificationUseCase.send(
                    new NotificationEvent(
                            List.of(NotificationChannel.EMAIL), email, subject, body, null, null, payload),
                    settings.tenantId());
        }
        for (UUID webhookId : enabledWebhooks) {
            sendNotificationUseCase.send(
                    new NotificationEvent(
                            List.of(NotificationChannel.WEBHOOK), null, null, null, null, webhookId, payload),
                    settings.tenantId());
        }
        for (String userId : participants(candidate.id())) {
            sendNotificationUseCase.send(
                    new NotificationEvent(
                            List.of(NotificationChannel.BROWSER), null, null, null, userId, null, payload),
                    settings.tenantId());
        }
    }

    private static Map<String, Object> payload(
            SlaAlertCandidate candidate, SlaStatus status, String subject, String body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "SLA_THRESHOLD_CROSSED");
        payload.put("title", subject);
        payload.put("body", body);
        payload.put("dossierId", candidate.id().toString());
        payload.put("reference", candidate.reference());
        payload.put("dossierTypeCode", candidate.dossierTypeCode());
        payload.put("slaStatus", status.name());
        return Map.copyOf(payload);
    }

    private List<UUID> enabledWebhooks(TenantSlaSettings settings) {
        List<UUID> enabled = new ArrayList<>();
        for (UUID webhookId : settings.alertWebhookIds()) {
            Optional<NotificationWebhookConfig> config =
                    webhookRepository.findByIdAndTenantId(webhookId, settings.tenantId());
            if (config.isPresent() && config.get().enabled()) {
                enabled.add(webhookId);
            }
        }
        return List.copyOf(enabled);
    }

    private Set<String> participants(UUID dossierId) {
        Set<String> userIds = new LinkedHashSet<>();
        for (DossierWorkflowLink link : workflowLinkRepository.findByDossierIdOrderBySequence(dossierId)) {
            for (String userId : participantPort.findParticipantUserIds(link)) {
                if (userId != null && !userId.isBlank()) {
                    userIds.add(userId);
                }
            }
        }
        return userIds;
    }
}
