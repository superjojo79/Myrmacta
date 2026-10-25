package be.myrmacta.domain.workflow.portal.usecase;

import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.portal.PortalIdentityFieldCheck;
import be.myrmacta.domain.workflow.portal.PortalParticipantNotifier;
import be.myrmacta.domain.workflow.portal.model.PortalEmails;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityField;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import be.myrmacta.domain.workflow.portal.port.in.IssuePortalLinkUseCase;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §68 D-PORTAL-ISSUE — à la création d'une tâche externe, crée un lien {@code PENDING_SEND}
 * (le job d'envoi génère ensuite le jeton) ou {@code NOT_SENT} avec sa raison. Jamais d'exception
 * pour une adresse ou un schéma manquant : le process ne doit pas partir en incident
 * (D-PORTAL-NO-ADDRESS), l'instructeur est notifié et renverra le lien.
 */
public final class IssuePortalLinkUseCaseImpl implements IssuePortalLinkUseCase {

    public static final String CREATED_BY_ENGINE = "flowable-task-created";

    private final String tenantId;
    private final PortalLinkRepository repository;
    private final TaskQuestionSchemaRepository schemaRepository;
    private final PortalRecipientResolverPort recipientResolver;
    private final PortalParticipantNotifier notifier;
    private final Clock clock;
    private final int defaultTtlDays;
    private final int maxTtlDays;
    private final PortalIdentityFieldCheck identityFieldCheck;

    public IssuePortalLinkUseCaseImpl(
            String tenantId,
            PortalLinkRepository repository,
            TaskQuestionSchemaRepository schemaRepository,
            PortalRecipientResolverPort recipientResolver,
            PortalParticipantNotifier notifier,
            Clock clock,
            int defaultTtlDays,
            int maxTtlDays) {
        this(tenantId, repository, schemaRepository, recipientResolver, notifier, clock, defaultTtlDays, maxTtlDays,
                PortalIdentityFieldCheck.NONE);
    }

    public IssuePortalLinkUseCaseImpl(
            String tenantId,
            PortalLinkRepository repository,
            TaskQuestionSchemaRepository schemaRepository,
            PortalRecipientResolverPort recipientResolver,
            PortalParticipantNotifier notifier,
            Clock clock,
            int defaultTtlDays,
            int maxTtlDays,
            PortalIdentityFieldCheck identityFieldCheck) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository");
        this.recipientResolver = Objects.requireNonNull(recipientResolver, "recipientResolver");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.defaultTtlDays = defaultTtlDays;
        this.maxTtlDays = maxTtlDays;
        this.identityFieldCheck = Objects.requireNonNull(identityFieldCheck, "identityFieldCheck");
    }

    @Override
    public Optional<PortalLink> onExternalTaskCreated(ExternalTaskCreated event) {
        Objects.requireNonNull(event, "event");
        if (event.dossierId() == null || !repository.findLiveByTaskId(event.taskId()).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(issue(event.taskId(), event.processInstanceId(), event.dossierId(),
                event.definition().schemaKey(),
                event.definition().recipientSource(),
                event.definition().ttlDays(),
                event.definition().identityField(),
                CREATED_BY_ENGINE));
    }

    /**
     * Partagé avec {@link ManagePortalLinkUseCaseImpl} (« Envoyer » / « Renvoyer ») : l'adresse est
     * relue à chaque émission, pour qu'une correction du champ par l'instructeur soit prise en compte.
     */
    PortalLink issue(
            String taskId, String processInstanceId, UUID dossierId, String schemaKey,
            be.myrmacta.domain.workflow.portal.model.PortalRecipientSource source, Integer requestedTtlDays,
            PortalIdentityField identityField, String createdBy) {
        Instant now = clock.instant();
        int ttl = Math.min(requestedTtlDays == null ? defaultTtlDays : requestedTtlDays, maxTtlDays);
        UUID id = UUID.randomUUID();
        Object raw = recipientResolver.resolve(source, dossierId, processInstanceId).orElse(null);
        Optional<String> email = PortalEmails.normalize(raw);
        PortalLinkStatusReason reason = null;
        if (schemaRepository.findByKey(tenantId, schemaKey).isEmpty()) {
            reason = PortalLinkStatusReason.SCHEMA_MISSING;
        } else if (raw == null || (raw instanceof String s && s.isBlank())) {
            reason = PortalLinkStatusReason.NO_ADDRESS;
        } else if (email.isEmpty()) {
            reason = PortalLinkStatusReason.INVALID_ADDRESS;
        } else if (identityField != null && !identityFieldCheck.isUsable(dossierId, identityField)) {
            reason = PortalLinkStatusReason.INVALID_IDENTITY_FIELD;
        }
        PortalLink link = reason == null
                ? PortalLink.pendingSend(id, tenantId, dossierId, taskId, processInstanceId, schemaKey, email.get(),
                        ttl, now, createdBy)
                : PortalLink.notSent(id, tenantId, dossierId, taskId, processInstanceId, schemaKey,
                        email.orElse(null), ttl, reason, now, createdBy);
        repository.insert(link);
        if (link.status() == PortalLinkStatus.NOT_SENT) {
            notifier.notifyNotSent(link);
        }
        return link;
    }
}
