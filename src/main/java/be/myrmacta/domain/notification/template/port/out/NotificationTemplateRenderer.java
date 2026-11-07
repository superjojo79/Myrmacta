package be.myrmacta.domain.notification.template.port.out;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import java.util.Map;

@FunctionalInterface
public interface NotificationTemplateRenderer {

    /**
     * Rend l'objet et le corps texte (mode TEXT) et le corps HTML (mode HTML, nettoyé). Évaluation complète et stricte.
     *
     * @throws TemplateProcessingException si le rendu échoue ; le message nomme la partie et l'expression, jamais une
     *     valeur
     */
    RenderedNotification render(NotificationTemplate template, Map<String, Object> model)
            throws TemplateProcessingException;
}
