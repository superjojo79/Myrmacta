package be.myrmacta.domain.notification.template.port.out;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;

@FunctionalInterface
public interface NotificationTemplateChecker {

    /**
     * Contrôle sans exécuter : syntaxe et grammaire d'expression des trois parties, puis politique du corps HTML. Refuse,
     * ne nettoie jamais en silence.
     *
     * @throws NotificationTemplateValidationException nomme la partie ({@code subject}, {@code textBody},
     *     {@code htmlBody}) et la règle violée
     */
    void check(NotificationTemplate template) throws NotificationTemplateValidationException;
}
