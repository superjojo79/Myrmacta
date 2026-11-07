package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import be.myrmacta.domain.notification.template.port.in.ResolveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.util.Objects;

public final class ResolveNotificationTemplateUseCaseImpl implements ResolveNotificationTemplateUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;

    public ResolveNotificationTemplateUseCaseImpl(String tenantId, NotificationTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public NotificationTemplate resolve(TemplateResolutionKey key) {
        Objects.requireNonNull(key, "key");
        TemplateResolutionKey normalized = new TemplateResolutionKey(
                NotificationTemplateSupport.requireKey(key.templateKey()),
                NotificationTemplateSupport.normalizeOptional(key.variantKey()),
                NotificationTemplateSupport.normalizeOptional(key.locale()));
        return repository
                .findBestActive(tenantId, normalized.templateKey(), normalized.variantKey(), normalized.locale())
                .orElseThrow(() -> new NotificationTemplateNotFoundException(normalized));
    }
}
