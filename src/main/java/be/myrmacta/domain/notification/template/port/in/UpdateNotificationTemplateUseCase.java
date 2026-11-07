package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import java.util.UUID;

public interface UpdateNotificationTemplateUseCase {

    /**
     * Met à jour un brouillon. Un champ nul est inchangé ; un {@code htmlBody} vide supprime le corps HTML.
     *
     * @throws be.myrmacta.domain.notification.template.model.NotificationTemplateStateException si le gabarit n'est pas un
     *     brouillon
     */
    NotificationTemplate update(UpdateCommand command);

    record UpdateCommand(
            UUID id, String label, String subject, String textBody, String htmlBody, String updatedBy) {}
}
