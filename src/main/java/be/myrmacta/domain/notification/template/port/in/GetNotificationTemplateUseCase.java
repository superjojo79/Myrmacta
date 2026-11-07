package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import java.util.UUID;

public interface GetNotificationTemplateUseCase {

    NotificationTemplate get(UUID id);
}
