package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateStateException;
import be.myrmacta.domain.notification.template.port.in.ArchiveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ArchiveNotificationTemplateUseCaseImpl implements ArchiveNotificationTemplateUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;

    public ArchiveNotificationTemplateUseCaseImpl(String tenantId, NotificationTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public NotificationTemplate archive(UUID id, String updatedBy) {
        NotificationTemplate current = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(id));
        if (current.status() != TemplateStatus.ACTIVE) {
            throw new NotificationTemplateStateException("Only ACTIVE notification templates can be archived");
        }
        return repository.save(current.withStatus(
                TemplateStatus.ARCHIVED, Instant.now(), NotificationTemplateSupport.updatedByOrSystem(updatedBy)));
    }
}
