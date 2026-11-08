package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateStateException;
import be.myrmacta.domain.notification.template.port.in.UpdateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.time.Instant;
import java.util.Objects;

public final class UpdateNotificationTemplateUseCaseImpl implements UpdateNotificationTemplateUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;
    private final NotificationTemplateLimits limits;

    public UpdateNotificationTemplateUseCaseImpl(
            String tenantId, NotificationTemplateRepository repository, NotificationTemplateLimits limits) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public NotificationTemplate update(UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        NotificationTemplate current = repository
                .findByIdAndTenantId(command.id(), tenantId)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(command.id()));
        if (current.status() != TemplateStatus.DRAFT) {
            throw new NotificationTemplateStateException("Only DRAFT notification templates can be updated");
        }
        String label = command.label() != null
                ? NotificationTemplateSupport.optionalLabel(command.label())
                : current.label();
        String subject = command.subject() != null
                ? NotificationTemplateSupport.requireSubject(command.subject(), limits)
                : current.subject();
        String textBody = command.textBody() != null
                ? NotificationTemplateSupport.requireTextBody(command.textBody(), limits)
                : current.textBody();
        String htmlBody = command.htmlBody() != null
                ? NotificationTemplateSupport.optionalHtmlBody(command.htmlBody(), limits)
                : current.htmlBody();
        return repository.save(new NotificationTemplate(
                current.id(),
                current.templateKey(),
                current.variantKey(),
                current.locale(),
                label,
                current.version(),
                current.status(),
                subject,
                textBody,
                htmlBody,
                current.tenantId(),
                current.createdAt(),
                Instant.now(),
                NotificationTemplateSupport.updatedByOrSystem(command.updatedBy())));
    }
}
