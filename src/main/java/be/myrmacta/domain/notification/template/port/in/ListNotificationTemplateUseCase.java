package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;

public interface ListNotificationTemplateUseCase {

    /** Liste paginée ; les corps sont vides (le détail les porte). */
    PageResult<NotificationTemplate> list(String templateKey, PageQuery query);
}
