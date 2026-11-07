package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.notification.model.NotificationValidationException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import be.myrmacta.domain.notification.template.port.in.RenderNotificationUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Issue #19 (TODO §123) — rend le gabarit d'une brique « Notifier » avec le modèle de
 * l'exécution : celui des expressions métier ({@link BusinessExpressionContext}, champs déclarés non sensibles, échec
 * fermé si le type est introuvable), moins {@code connectorResult}. Un gabarit absent ou un rendu en échec (variable ou
 * propriété absente, partie hors bornes) devient une {@link NotificationValidationException} : incident si la brique est
 * asynchrone, refus 422 sinon, et rien n'est écrit dans l'outbox. Le message nomme le gabarit, jamais une valeur.
 */
@Component
class NotificationTemplateRendering {

    private final RenderNotificationUseCase render;
    private final GetDossierUseCase getDossier;
    private final DossierTypeRepository dossierTypes;
    private final TenantProperties tenantProperties;
    private final ObjectMapper objectMapper;

    NotificationTemplateRendering(
            RenderNotificationUseCase render,
            GetDossierUseCase getDossier,
            DossierTypeRepository dossierTypes,
            TenantProperties tenantProperties,
            ObjectMapper objectMapper) {
        this.render = Objects.requireNonNull(render, "render");
        this.getDossier = Objects.requireNonNull(getDossier, "getDossier");
        this.dossierTypes = Objects.requireNonNull(dossierTypes, "dossierTypes");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    RenderedNotification render(DelegateExecution execution, TemplateResolutionKey key) {
        Map<String, Object> model = new LinkedHashMap<>(BusinessExpressionContext.forExecution(
                execution, getDossier, dossierTypes, tenantProperties, objectMapper).context());
        model.remove("connectorResult");
        try {
            return render.render(key, model);
        } catch (NotificationTemplateNotFoundException | TemplateProcessingException failed) {
            throw new NotificationValidationException(
                    "notificationTemplate " + key.templateKey() + ": " + failed.getMessage());
        }
    }
}
