package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.port.in.GetNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import java.util.Objects;
import java.util.UUID;

public final class GetNotificationTemplateUseCaseImpl implements GetNotificationTemplateUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;

    public GetNotificationTemplateUseCaseImpl(String tenantId, NotificationTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public NotificationTemplate get(UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(id));
    }
}
