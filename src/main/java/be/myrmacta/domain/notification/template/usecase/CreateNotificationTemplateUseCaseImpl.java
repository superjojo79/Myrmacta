package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreateNotificationTemplateUseCaseImpl implements CreateNotificationTemplateUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;
    private final NotificationTemplateLimits limits;

    public CreateNotificationTemplateUseCaseImpl(
            String tenantId, NotificationTemplateRepository repository, NotificationTemplateLimits limits) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public NotificationTemplate create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        String key = NotificationTemplateSupport.requireKey(command.templateKey());
        String variant = NotificationTemplateSupport.optionalVariant(command.variantKey());
        String locale = NotificationTemplateSupport.optionalLocale(command.locale());
        String label = NotificationTemplateSupport.optionalLabel(command.label());
        String subject = NotificationTemplateSupport.requireSubject(command.subject(), limits);
        String textBody = NotificationTemplateSupport.requireTextBody(command.textBody(), limits);
        String htmlBody = NotificationTemplateSupport.optionalHtmlBody(command.htmlBody(), limits);
        int version = repository.nextVersion(tenantId, key, variant, locale);
        Instant now = Instant.now();
        return repository.save(new NotificationTemplate(
                UUID.randomUUID(),
                key,
                variant,
                locale,
                label,
                version,
                TemplateStatus.DRAFT,
                subject,
                textBody,
                htmlBody,
                tenantId,
                now,
                now,
                NotificationTemplateSupport.updatedByOrSystem(command.updatedBy())));
    }
}
