package be.myrmacta.domain.deadline.usecase;

import be.myrmacta.domain.deadline.model.DeadlineCandidate;
import be.myrmacta.domain.deadline.model.DeadlineLevel;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.in.EvaluateDeadlinesUseCase;
import be.myrmacta.domain.deadline.port.out.DeadlineAlertClaims;
import be.myrmacta.domain.deadline.port.out.DeadlineCandidates;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.reporting.UnitOfWork;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.WorkflowTaskView;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.port.out.WorkflowVisualizationPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §115 (D-DEADLINE-ESCALATE) — rappel J-n aux participants, puis, dès le lendemain du terme, escalade : groupe prévenu, tâches
 * ouvertes du process actif réaffectées au groupe, note de trace. Chaque palier est pris en base avant d'agir (une seule fois par
 * dossier, palier et date) ; chaque dossier a sa propre unité de travail.
 */
public final class EvaluateDeadlinesUseCaseImpl implements EvaluateDeadlinesUseCase {

    static final String SYSTEM = "system";
    private static final int TASK_PAGE = 100;

    private final DeadlineSettings settings;
    private final DeadlineCandidates candidates;
    private final DeadlineAlertClaims claims;
    private final DossierWorkflowLinkRepository links;
    private final WorkflowParticipantPort participants;
    private final SendNotificationUseCase notifications;
    private final UserGroupsUseCase groups;
    private final WorkflowVisualizationPort engine;
    private final WorkflowLinkGroupRepository linkGroups;
    private final DossierElementRepository elements;

    public EvaluateDeadlinesUseCaseImpl(
            DeadlineSettings settings,
            DeadlineCandidates candidates,
            DeadlineAlertClaims claims,
            DossierWorkflowLinkRepository links,
            WorkflowParticipantPort participants,
            SendNotificationUseCase notifications,
            UserGroupsUseCase groups,
            WorkflowVisualizationPort engine,
            WorkflowLinkGroupRepository linkGroups,
            DossierElementRepository elements) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.links = Objects.requireNonNull(links, "links");
        this.participants = Objects.requireNonNull(participants, "participants");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.linkGroups = Objects.requireNonNull(linkGroups, "linkGroups");
        this.elements = Objects.requireNonNull(elements, "elements");
    }

    @Override
    public int scan(String tenantId, Instant now, UnitOfWork unitOfWork) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(unitOfWork, "unitOfWork");
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        int failures = 0;
        for (DeadlineSetting setting : settings.findAll(tenantId)) {
            for (DeadlineCandidate candidate : candidates.list(setting)) {
                Optional<LocalDate> deadline = parse(candidate.rawDeadline());
                if (deadline.isEmpty()) {
                    continue;
                }
                try {
                    unitOfWork.run(() -> evaluate(tenantId, setting, candidate, deadline.get(), today, now));
                } catch (RuntimeException ex) {
                    failures++;
                }
            }
        }
        return failures;
    }

    private void evaluate(String tenantId, DeadlineSetting setting, DeadlineCandidate candidate, LocalDate deadline, LocalDate today,
            Instant now) {
        if (today.isAfter(deadline)) {
            if (claims.claim(tenantId, candidate.dossierId(), DeadlineLevel.ESCALADE, deadline, now)) {
                escalate(tenantId, setting, candidate, deadline, now);
            }
            return;
        }
        if (setting.reminderDays() > 0 && !today.isBefore(deadline.minusDays(setting.reminderDays()))
                && claims.claim(tenantId, candidate.dossierId(), DeadlineLevel.RAPPEL, deadline, now)) {
            String title = "Échéance proche — " + candidate.reference();
            String body = "Le dossier " + candidate.reference() + " arrive à échéance le " + deadline + ".";
            notifyUsers(tenantId, participantsOf(candidate.dossierId()), payload("DOSSIER_DEADLINE_REMINDER", title, body, candidate,
                    deadline));
        }
    }

    private void escalate(String tenantId, DeadlineSetting setting, DeadlineCandidate candidate, LocalDate deadline, Instant now) {
        Set<String> previousParticipants = participantsOf(candidate.dossierId());
        String group = existingGroup(setting.escalationGroup());
        List<String> reassigned = new ArrayList<>();
        Optional<DossierWorkflowLink> active = links.findActiveByDossierId(candidate.dossierId());
        if (group != null && active.isPresent() && hasInstance(active.get())) {
            DossierWorkflowLink link = active.get();
            for (WorkflowTaskView task : activeTasks(link, tenantId)) {
                engine.reassignTaskToGroup(link.processInstanceId(), task.taskId(), group, tenantId);
                reassigned.add(task.name() == null || task.name().isBlank() ? task.taskId() : task.name());
            }
            linkGroups.assign(tenantId, link.id(), group);
        }
        String title = "Échéance dépassée — " + candidate.reference();
        String body = "Le dossier " + candidate.reference() + " a dépassé son échéance du " + deadline + "."
                + (group == null ? "" : " Escaladé au groupe " + group + ".");
        Map<String, Object> payload = payload("DOSSIER_DEADLINE_ESCALATED", title, body, candidate, deadline);
        Set<String> recipients = new LinkedHashSet<>(previousParticipants);
        if (group != null) {
            recipients.addAll(groups.membersOf(group));
        }
        notifyUsers(tenantId, recipients, payload);
        elements.save(new DossierElement(UUID.randomUUID(), candidate.dossierId(), tenantId, ElementType.NOTE,
                noteLabel(deadline, setting.escalationGroup(), group, reassigned), null,
                noteMetadata(deadline, setting.escalationGroup(), group, reassigned), SYSTEM, now, null));
    }

    private String existingGroup(String code) {
        if (code == null) {
            return null;
        }
        try {
            return groups.get(code).code();
        } catch (UserGroupNotFoundException deleted) {
            return null;
        }
    }

    private List<WorkflowTaskView> activeTasks(DossierWorkflowLink link, String tenantId) {
        List<WorkflowTaskView> tasks = new ArrayList<>();
        int page = 0;
        while (true) {
            var result = engine.listActiveTasks(link.processInstanceId(), tenantId, PageQuery.of(page, TASK_PAGE));
            tasks.addAll(result.content());
            if (page + 1 >= result.totalPages() || result.content().isEmpty()) {
                return tasks;
            }
            page++;
        }
    }

    private Set<String> participantsOf(UUID dossierId) {
        Set<String> userIds = new LinkedHashSet<>();
        for (DossierWorkflowLink link : links.findByDossierIdOrderBySequence(dossierId)) {
            for (String userId : participants.findParticipantUserIds(link)) {
                if (userId != null && !userId.isBlank()) {
                    userIds.add(userId);
                }
            }
        }
        return userIds;
    }

    private void notifyUsers(String tenantId, Set<String> userIds, Map<String, Object> payload) {
        for (String userId : userIds) {
            notifications.send(new NotificationEvent(List.of(NotificationChannel.BROWSER), null, null, null, userId, null, payload),
                    tenantId);
        }
    }

    private static boolean hasInstance(DossierWorkflowLink link) {
        return link.processInstanceId() != null && !link.processInstanceId().isBlank();
    }

    private static Map<String, Object> payload(String type, String title, String body, DeadlineCandidate candidate, LocalDate deadline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("dossierId", candidate.dossierId().toString());
        payload.put("reference", candidate.reference());
        payload.put("dossierTypeCode", candidate.dossierTypeCode());
        payload.put("deadline", deadline.toString());
        return Map.copyOf(payload);
    }

    private static String noteLabel(LocalDate deadline, String configured, String group, List<String> reassigned) {
        String head = "Échéance du " + deadline + " dépassée";
        if (group == null) {
            return configured == null ? head + " : aucun groupe d'escalade" : head + " : groupe d'escalade introuvable (" + configured + ")";
        }
        if (reassigned.isEmpty()) {
            return head + " : groupe " + group + " prévenu, aucune tâche ouverte";
        }
        return head + " : " + reassigned.size() + " tâche(s) réaffectée(s) au groupe " + group + " (" + String.join(", ", reassigned) + ")";
    }

    private static Map<String, Object> noteMetadata(LocalDate deadline, String configured, String group, List<String> reassigned) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("event", "DEADLINE_ESCALATED");
        metadata.put("deadline", deadline.toString());
        if (configured != null) {
            metadata.put("escalationGroup", configured);
        }
        metadata.put("escalated", group != null);
        metadata.put("reassignedTasks", List.copyOf(reassigned));
        return metadata;
    }

    /** Une date ISO {@code AAAA-MM-JJ}, éventuellement suivie d'une heure ; toute autre valeur est ignorée. */
    static Optional<LocalDate> parse(String raw) {
        if (raw == null || raw.length() < 10) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(raw.substring(0, 10)));
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }
}
