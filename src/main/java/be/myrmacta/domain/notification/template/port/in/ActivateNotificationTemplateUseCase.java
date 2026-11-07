package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import java.util.UUID;

public interface ActivateNotificationTemplateUseCase {

    /**
     * Contrôle complet (syntaxe des trois parties et politique HTML), archive l'éventuelle version active de la même clé,
     * puis active le brouillon.
     */
    NotificationTemplate activate(UUID id, String updatedBy);
}
