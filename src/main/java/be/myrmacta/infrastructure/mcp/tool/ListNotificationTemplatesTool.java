package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code list_notification_templates} (TODO §123) : clés, variantes, langues, versions et statuts des gabarits de notification.
 * Ni l'objet ni les corps ne sont renvoyés.
 */
@Component
public class ListNotificationTemplatesTool {

    /** Métadonnées d'une version de gabarit. */
    public record Summary(
            UUID id,
            String templateKey,
            String variantKey,
            String locale,
            String label,
            int version,
            String status,
            Instant updatedAt,
            String updatedBy) {

        static Summary from(NotificationTemplate t) {
            return new Summary(t.id(), t.templateKey(), t.variantKey(), t.locale(), t.label(), t.version(),
                    t.status().name(), t.updatedAt(), t.updatedBy());
        }
    }

    private final ListNotificationTemplateUseCase listUseCase;

    public ListNotificationTemplatesTool(ListNotificationTemplateUseCase listUseCase) {
        this.listUseCase = Objects.requireNonNull(listUseCase, "listUseCase");
    }

    @McpTool(
            name = "list_notification_templates",
            description = "List notification templates (key, variant, locale, version, status; never the subject or the "
                    + "bodies), paginated.")
    public PageResult<Summary> listNotificationTemplates(
            @McpToolParam(required = false, description = "Template key filter") String templateKey,
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        PageQuery query = PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        PageResult<NotificationTemplate> result = listUseCase.list(templateKey, query);
        return PageResult.of(
                result.content().stream().map(Summary::from).toList(), result.page(), result.size(), result.totalElements());
    }
}
