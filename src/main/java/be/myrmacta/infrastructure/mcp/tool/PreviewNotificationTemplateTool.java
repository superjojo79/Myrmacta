package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.PreviewNotificationTemplateUseCase;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code preview_notification_template} (TODO §123) : rend un gabarit de notification avec un modèle d'exemple, sans rien
 * enregistrer ni envoyer. Soit par clé (dernière version, brouillon compris), soit par contenu fourni en direct. Le rendu est
 * strict comme à l'envoi : une variable absente fait échouer l'aperçu.
 */
@Component
public class PreviewNotificationTemplateTool {

    private static final TenantAdminGuard GUARD = new TenantAdminGuard();

    private final PreviewNotificationTemplateUseCase previewUseCase;
    private final ListNotificationTemplateUseCase listUseCase;
    private final NotificationTemplateProperties properties;
    private final McpAccessContextFactory accessContextFactory;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public PreviewNotificationTemplateTool(
            PreviewNotificationTemplateUseCase previewUseCase,
            ListNotificationTemplateUseCase listUseCase,
            NotificationTemplateProperties properties,
            McpAccessContextFactory accessContextFactory,
            tools.jackson.databind.ObjectMapper objectMapper) {
        this.previewUseCase = Objects.requireNonNull(previewUseCase, "previewUseCase");
        this.listUseCase = Objects.requireNonNull(listUseCase, "listUseCase");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @McpTool(
            name = "preview_notification_template",
            description = "Render a notification template (subject, plain-text body, HTML body) with a sample model and "
                    + "return the rendered parts. Nothing is stored or sent. Either give templateKey (renders the latest "
                    + "version of that key/variant/locale, draft included) or give subject and textBody (and optionally "
                    + "htmlBody) to render a template that is not saved. model is {\"dossier\": {\"reference\", \"type\", "
                    + "\"status\", \"label\", \"fields\": {...}}, \"variables\": {...}}; a variable the template uses "
                    + "but the model lacks makes the preview fail, as it would at send time.")
    public Map<String, Object> previewNotificationTemplate(
            @McpToolParam(required = false, description = "Key of a saved template (exclusive with subject/textBody)")
                    String templateKey,
            @McpToolParam(required = false, description = "Variant key, with templateKey (optional)") String variantKey,
            @McpToolParam(required = false, description = "Locale, with templateKey (optional)") String locale,
            @McpToolParam(required = false, description = "Subject to render (without templateKey)") String subject,
            @McpToolParam(required = false, description = "Plain-text body to render (without templateKey)")
                    String textBody,
            @McpToolParam(required = false, description = "HTML body to render (without templateKey), optional")
                    String htmlBody,
            @McpToolParam(required = false, description = "Sample model: {dossier: {...}, variables: {...}}")
                    Map<String, Object> model) {
        // Mêmes garde-fous que l'aperçu REST : rôle administrateur, taille du modèle.
        GUARD.requireTenantAdminOrApiService(accessContextFactory.currentActor());
        Map<String, Object> sample = model == null ? Map.of() : model;
        if (objectMapper.writeValueAsBytes(sample).length > properties.previewModelMaxBytes()) {
            throw new IllegalArgumentException("model exceeds " + properties.previewModelMaxBytes() + " bytes");
        }
        boolean byKey = templateKey != null && !templateKey.isBlank();
        boolean live = subject != null || textBody != null || htmlBody != null;
        if (byKey && live) {
            throw new IllegalArgumentException("give either templateKey or subject/textBody, not both");
        }
        PreviewNotificationTemplateUseCase.PreviewCommand command;
        if (byKey) {
            String key = NotificationTemplateSupport.requireKey(templateKey);
            NotificationTemplate latest = NotificationTemplateVersions.latest(
                    listUseCase,
                    key,
                    NotificationTemplateSupport.optionalVariant(variantKey),
                    NotificationTemplateSupport.optionalLocale(locale));
            if (latest == null) {
                throw new NotificationTemplateValidationException(
                        "templateKey", "no template '" + key + "' for this variant and locale");
            }
            command = new PreviewNotificationTemplateUseCase.PreviewCommand(latest.id(), null, null, null, sample);
        } else {
            McpArgs.requireNonBlank(subject, "subject");
            McpArgs.requireNonBlank(textBody, "textBody");
            command = new PreviewNotificationTemplateUseCase.PreviewCommand(null, subject, textBody, htmlBody, sample);
        }
        RenderedNotification rendered = previewUseCase.preview(command);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", rendered.subject());
        out.put("textBody", rendered.textBody());
        out.put("htmlBody", rendered.htmlBody());
        return out;
    }
}
