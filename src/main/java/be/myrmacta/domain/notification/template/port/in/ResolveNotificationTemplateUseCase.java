package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;

public interface ResolveNotificationTemplateUseCase {

    /**
     * Version ACTIVE qui correspond le mieux, par quatre niveaux : (variante, langue), (variante, défaut),
     * (défaut, langue), (défaut, défaut).
     *
     * @throws be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException si aucune ne convient
     */
    NotificationTemplate resolve(TemplateResolutionKey key);
}
