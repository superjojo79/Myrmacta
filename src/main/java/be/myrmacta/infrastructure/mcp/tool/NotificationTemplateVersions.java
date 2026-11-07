package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.Objects;

/** Recherche de la dernière version d'un gabarit de notification (clé, variante, langue) pour les outils MCP (TODO §123). */
final class NotificationTemplateVersions {

    private static final int LOOKUP_PAGE_SIZE = PageQuery.MAX_SIZE;
    /** Garde-fou : au-delà (versions x variantes x langues d'une même clé), on refuse plutôt que de décider sur une vue partielle. */
    private static final int LOOKUP_MAX_PAGES = 50;

    private NotificationTemplateVersions() {}

    /** Toutes les pages sont lues : la liste n'est pas triée par version. {@code null} si la combinaison n'existe pas. */
    static NotificationTemplate latest(
            ListNotificationTemplateUseCase listUseCase, String key, String variant, String locale) {
        NotificationTemplate best = null;
        for (int page = 0; page < LOOKUP_MAX_PAGES; page++) {
            PageResult<NotificationTemplate> result = listUseCase.list(key, PageQuery.of(page, LOOKUP_PAGE_SIZE));
            for (NotificationTemplate t : result.content()) {
                if (Objects.equals(t.variantKey(), variant)
                        && Objects.equals(t.locale(), locale)
                        && (best == null || t.version() > best.version())) {
                    best = t;
                }
            }
            if (page + 1 >= result.totalPages()) {
                return best;
            }
        }
        throw new IllegalStateException(
                "notification template key '" + key + "' has too many versions to be resolved over MCP");
    }
}
