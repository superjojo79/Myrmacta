package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.RenderedNotification;
import java.util.Map;
import java.util.UUID;

public interface PreviewNotificationTemplateUseCase {

    /**
     * Rend un gabarit donné par son identifiant (brouillon compris) ou par son contenu, avec un modèle d'exemple. Le
     * contenu subit le même contrôle qu'à l'activation.
     */
    RenderedNotification preview(PreviewCommand command);

    /**
     * @param id gabarit stocké ; exclusif avec {@code subject}/{@code textBody}/{@code htmlBody}
     */
    record PreviewCommand(UUID id, String subject, String textBody, String htmlBody, Map<String, Object> model) {

        public boolean isLive() {
            return id == null;
        }
    }
}
