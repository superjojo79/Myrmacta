package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import be.myrmacta.domain.notification.template.port.in.RenderNotificationUseCase;
import be.myrmacta.domain.notification.template.port.in.ResolveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRenderer;
import java.util.Map;
import java.util.Objects;

public final class RenderNotificationUseCaseImpl implements RenderNotificationUseCase {

    private final ResolveNotificationTemplateUseCase resolveUseCase;
    private final NotificationTemplateRenderer renderer;

    public RenderNotificationUseCaseImpl(
            ResolveNotificationTemplateUseCase resolveUseCase, NotificationTemplateRenderer renderer) {
        this.resolveUseCase = Objects.requireNonNull(resolveUseCase, "resolveUseCase");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public RenderedNotification render(TemplateResolutionKey key, Map<String, Object> model) {
        NotificationTemplate template = resolveUseCase.resolve(key);
        return renderer.render(template, model == null ? Map.of() : model);
    }
}
