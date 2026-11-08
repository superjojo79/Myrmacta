package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.port.in.PreviewNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRenderer;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PreviewNotificationTemplateUseCaseImpl implements PreviewNotificationTemplateUseCase {

    private static final String PREVIEW_KEY = "preview";

    private final String tenantId;
    private final NotificationTemplateRepository repository;
    private final NotificationTemplateChecker checker;
    private final NotificationTemplateRenderer renderer;
    private final NotificationTemplateLimits limits;

    public PreviewNotificationTemplateUseCaseImpl(
            String tenantId,
            NotificationTemplateRepository repository,
            NotificationTemplateChecker checker,
            NotificationTemplateRenderer renderer,
            NotificationTemplateLimits limits) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public RenderedNotification preview(PreviewCommand command) {
        Objects.requireNonNull(command, "command");
        NotificationTemplate template = command.isLive() ? transientTemplate(command) : stored(command);
        checker.check(template);
        Map<String, Object> model = command.model() == null ? Map.of() : command.model();
        return renderer.render(template, model);
    }

    private NotificationTemplate stored(PreviewCommand command) {
        if (command.subject() != null || command.textBody() != null || command.htmlBody() != null) {
            throw new NotificationTemplateValidationException("id", "cannot be combined with subject, textBody or htmlBody");
        }
        UUID id = command.id();
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(id));
    }

    private NotificationTemplate transientTemplate(PreviewCommand command) {
        Instant now = Instant.now();
        return new NotificationTemplate(
                UUID.randomUUID(),
                PREVIEW_KEY,
                null,
                null,
                null,
                0,
                TemplateStatus.DRAFT,
                NotificationTemplateSupport.requireSubject(command.subject(), limits),
                NotificationTemplateSupport.requireTextBody(command.textBody(), limits),
                NotificationTemplateSupport.optionalHtmlBody(command.htmlBody(), limits),
                tenantId,
                now,
                now,
                "preview");
    }
}
