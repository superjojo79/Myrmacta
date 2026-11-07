package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateStateException;
import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** À exécuter dans une seule transaction : archive la version active, puis active le brouillon. */
public final class ActivateNotificationTemplateUseCaseImpl implements ActivateNotificationTemplateUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;
    private final NotificationTemplateChecker checker;

    public ActivateNotificationTemplateUseCaseImpl(
            String tenantId, NotificationTemplateRepository repository, NotificationTemplateChecker checker) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.checker = Objects.requireNonNull(checker, "checker");
    }

    @Override
    public NotificationTemplate activate(UUID id, String updatedBy) {
        NotificationTemplate current = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(id));
        if (current.status() != TemplateStatus.DRAFT) {
            throw new NotificationTemplateStateException("Only DRAFT notification templates can be activated");
        }
        checker.check(current);
        Instant now = Instant.now();
        String by = NotificationTemplateSupport.updatedByOrSystem(updatedBy);
        repository
                .findActiveExact(tenantId, current.templateKey(), current.variantKey(), current.locale())
                .ifPresent(active -> repository.save(active.withStatus(TemplateStatus.ARCHIVED, now, by)));
        return repository.save(current.withStatus(TemplateStatus.ACTIVE, now, by));
    }
}
