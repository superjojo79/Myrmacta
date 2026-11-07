package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.port.in.NewNotificationTemplateVersionUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class NewNotificationTemplateVersionUseCaseImpl implements NewNotificationTemplateVersionUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;

    public NewNotificationTemplateVersionUseCaseImpl(String tenantId, NotificationTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public NotificationTemplate newVersion(UUID id, String updatedBy) {
        NotificationTemplate current = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(id));
        int version = repository.nextVersion(tenantId, current.templateKey(), current.variantKey(), current.locale());
        Instant now = Instant.now();
        return repository.save(new NotificationTemplate(
                UUID.randomUUID(),
                current.templateKey(),
                current.variantKey(),
                current.locale(),
                current.label(),
                version,
                TemplateStatus.DRAFT,
                current.subject(),
                current.textBody(),
                current.htmlBody(),
                tenantId,
                now,
                now,
                NotificationTemplateSupport.updatedByOrSystem(updatedBy)));
    }
}
