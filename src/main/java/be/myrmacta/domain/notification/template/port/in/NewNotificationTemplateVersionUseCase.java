package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import java.util.UUID;

public interface NewNotificationTemplateVersionUseCase {

    /** Crée un brouillon, version suivante de la même clé, copié depuis le gabarit donné. */
    NotificationTemplate newVersion(UUID id, String updatedBy);
}
