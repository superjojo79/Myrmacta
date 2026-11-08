package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;

public interface CreateNotificationTemplateUseCase {

    /** Crée un brouillon. La syntaxe n'est contrôlée qu'à l'activation, comme pour les templates de document. */
    NotificationTemplate create(CreateCommand command);

    record CreateCommand(
            String templateKey,
            String variantKey,
            String locale,
            String label,
            String subject,
            String textBody,
            String htmlBody,
            String updatedBy) {}
}
