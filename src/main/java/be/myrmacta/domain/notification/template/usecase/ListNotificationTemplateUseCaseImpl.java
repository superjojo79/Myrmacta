package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.Objects;

public final class ListNotificationTemplateUseCaseImpl implements ListNotificationTemplateUseCase {

    private final String tenantId;
    private final NotificationTemplateRepository repository;

    public ListNotificationTemplateUseCaseImpl(String tenantId, NotificationTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public PageResult<NotificationTemplate> list(String templateKey, PageQuery query) {
        Objects.requireNonNull(query, "query");
        PageResult<NotificationTemplate> page = repository.pageByTenantId(
                tenantId, NotificationTemplateSupport.normalizeOptional(templateKey), query);
        return PageResult.of(
                page.content().stream().map(NotificationTemplate::withoutBodies).toList(),
                page.page(),
                page.size(),
                page.totalElements());
    }
}
