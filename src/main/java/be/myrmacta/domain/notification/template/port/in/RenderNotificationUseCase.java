package be.myrmacta.domain.notification.template.port.in;

import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import java.util.Map;

public interface RenderNotificationUseCase {

    /**
     * Résout la version active puis la rend avec le modèle. Une variable ou propriété absente ou nulle fait échouer le
     * rendu ({@link be.myrmacta.domain.document.model.TemplateProcessingException}) ; le message nomme le gabarit et
     * l'expression, jamais une valeur.
     */
    RenderedNotification render(TemplateResolutionKey key, Map<String, Object> model);
}
